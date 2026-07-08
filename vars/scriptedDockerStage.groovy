// vars/scriptedDockerStage.groovy

import org.jenkinsci.plugins.pipeline.modeldefinition.Utils

/**
 * scriptedDockerStage
 *
 * Get a docker stage in scripted syntax.
 *
 * @param kwargs Map containing the following optional arguments (empty strings yield defaults):
 *          name                    the docker stage name
 *          runStage                optional additional condition to determine if the stage runs
 *          jobStatus               Map of status for each stage in the job/build
 *          dockerTag               the docker image tag to use for the build
 *          dockerBuildArgs         optional docker build arguments
 *          installScript           optional script to run to install rpms; defaults to ''
 *          buildScript             optional script to run to build dependencies; defaults to ''
 *          sconsBuildArgs          optional scons build arguments
 *          valgrindSconsBuildArgs  optional scons build arguments for valgrind build
 *          generateRpmsScript      optional script to run to generate rpms; defaults to ''
 *          uploadTarget            the distro to use when uploading rpms; defaults to distro
 *          publishHtmlArgs         optional arguments to pass to publishHTML()
 *          archiveArtifactsArgs    optional arguments to pass to archiveArtifacts()
 * @return a scripted stage to run in a pipeline
 */
Map call(Map kwargs = [:]) {
    String name = kwargs.get('name', 'Unknown Docker Stage')
    Boolean runStage = kwargs.get('runStage', true)
    Map jobStatus = kwargs.get('jobStatus', [:])
    String dockerTag = jobStatusKey("build-${uploadTarget}-${compiler}").toLowerCase()
    String dockerBuildArgs = kwargs.get('dockerBuildArgs', '')

    // Optional scripts and arguments that drive the stage steps
    String installScript = kwargs.get('installScript', '')
    String buildScript = kwargs.get('buildScript', '')
    Map sconsBuildArgs = kwargs.get('sconsBuildArgs', [:])
    Map valgrindSconsBuildArgs = kwargs.get('valgrindSconsBuildArgs', [:])
    String generateRpmsScript = kwargs.get('generateRpmsScript', '')
    String uploadTarget = kwargs.get('uploadTarget', distro)
    Map publishHtmlArgs = kwargs.get('publishHtmlArgs', [:])
    Map archiveArtifactsArgs = kwargs.get('archiveArtifactsArgs', [:])

    // String distro = kwargs.get('distro', 'el8')
    // String rpmDistro = kwargs.get('rpmDistro', distro)
    // String release = kwargs.get('release', env.DAOS_RELVAL)
    // String compiler = kwargs.get('compiler', 'gcc')
    // String artifacts = kwargs.get('artifacts', "config.log-${distro}-${compiler}")
    // String bullseye = 'false'
    // if (compiler == 'covc') {
    //     bullseye = 'true'
    // }

    return {
        stage("${name}") {
            if (!runStage) {
                println("[${name}] Marking docker stage as skipped")
                Utils.markStageSkippedForConditional("${name}")
                return
            }
            node('docker_runner') {
                println("[${name}] Check out from version control")
                checkoutScm(pruneStaleBranch: true)

                def dockerImage = docker.build(dockerTag, dockerBuildArgs)
                try {
                    dockerImage.inside() {
                        if (installScript) {
                            sh label: 'Install RPMs',
                                script: "${installScript}"
                                // script: "./ci/rpm/install_deps.sh ${rpmDistro} ${release} ${bullseye}"
                        }
                        if (buildScript) {
                            sh label: 'Build deps',
                                script: "${buildScript}"
                                // script: "./ci/rpm/build_deps.sh ${bullseye}" + ' ${BULLSEYE_KEY}'
                        }
                        if (sconsBuildArgs) {
                            job_step_update(sconsBuild(sconsBuildArgs))
                        }
                        if (valgrindSconsBuildArgs) {
                            // For non-release builds, create a separate build with the valgrind
                            // tag for NLT memcheck testing.  This is necessary to avoid problems
                            // caused by valgrind being confused by the Go runtime. We don't want
                            // to use the valgrind build for normal testing because it is much
                            // slower. BUILD_TYPE=dev is set for PR/dev builds in sconsArgs(), and
                            // TARGET_TYPE=release is used to select pre-built cached prerequisites.
                            job_step_update(sconsBuild(valgrindSconsBuildArgs))
                            sh label: 'Stash valgrind install tree for NLT',
                               script: 'tar -C / -cf opt-daos-valgrind.tar opt/daos'
                            stash(name: 'opt-daos-valgrind', includes: 'opt-daos-valgrind.tar')
                        }
                        if (generateRpmsScript) {
                            sh label: 'Generate RPMs',
                                script: "${generateRpmsScript}"
                                // script: "./ci/rpm/gen_rpms.sh ${rpmDistro} ${release} ${bullseye}"
                        }
                    }
                } catch (Exception e) {
                    println("[${name}] Caught exception in try: ${e}")
                    if (sconsBuildArgs) {
                        // Unsuccessful actions
                        sh """if [ -f config.log ]; then
                                mv config.log ${artifacts}
                            fi"""
                        archiveArtifacts artifacts: "${artifacts}", allowEmptyArchive: true
                    }
                    jobStatusUpdate(jobStatus, name, 'FAILURE')
                    throw e
                } finally {
                    // Cleanup actions
                    try{
                        if (uploadTarget) {
                            println("[${name}] Running uploadNewRPMs()")
                            uploadNewRPMs(uploadTarget, 'cleanup')
                        }
                        if (publishHtmlArgs) {
                            println("[${name}] Running publishHTML()")
                            publishHTML(publishHtmlArgs)
                        }
                        if (archiveArtifactsArgs) {
                            println("[${name}] Running archiveArtifacts()")
                            archiveArtifacts(archiveArtifactsArgs)
                        }
                        jobStatusUpdate(jobStatus, name)
                    } catch (Exception e) {
                        println("[${name}] Caught exception in finally: ${e}")
                        jobStatusUpdate(jobStatus, name, 'FAILURE')
                        throw e
                    }
                }
            }
            println("[${name}] Finished with ${jobStatus}")
        }
    }
}
