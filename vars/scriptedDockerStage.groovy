/* groovylint-disable NestedBlockDepth */
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
 *          stepMethod              method to call to run the stage
 *          stepMethodArgs          arguments to pass to the stepMethod; defaults to [:]
 *          configLog               optional config.log name to archive upon exception
 *          valgrindSconsBuildArgs  optional scons build arguments for valgrind build
 *          generateRpmsScript      optional script to run to generate rpms; defaults to ''
 *          buildRpmPostArgs        optional arguments to pass to buildRpmPost(); defaults to [:]
 *          publishHtmlArgs         optional arguments to pass to publishHTML()
 *          archiveArtifactsArgs    optional arguments to pass to archiveArtifacts()
 * @return a scripted stage to run in a pipeline
 */
/* groovylint-disable-next-line MethodSize */
Map call(Map kwargs = [:]) {
    String name = kwargs.get('name', 'Unknown Docker Stage')
    Boolean runStage = kwargs.get('runStage', true)
    Map jobStatus = kwargs.get('jobStatus', null) ?: [:]
    String dockerTag = kwargs.get('dockerTag', 'unknown-docker-tag')
    String dockerBuildArgs = kwargs.get('dockerBuildArgs', '')

    // Optional scripts and arguments that drive the stage steps
    String installScript = kwargs.get('installScript', '')
    String buildScript = kwargs.get('buildScript', '')
    Closure stepMethod = kwargs.get('stepMethod')
    Map stepMethodArgs = kwargs.get('stepMethodArgs', null) ?: [:]
    String configLog = kwargs.get('configLog', '')
    Map valgrindSconsBuildArgs = kwargs.get('valgrindSconsBuildArgs', null) ?: [:]
    String generateRpmsScript = kwargs.get('generateRpmsScript', '')
    Map buildRpmPostArgs = kwargs.get('buildRpmPostArgs', null) ?: [:]
    Map publishHtmlArgs = kwargs.get('publishHtmlArgs', null) ?: [:]
    Map archiveArtifactsArgs = kwargs.get('archiveArtifactsArgs', null) ?: [:]

    return {
        stage("${name}") {
            println("[${name}] Starting stage: kwargs=${kwargs}")

            if (!runStage) {
                println("[${name}] Marking docker stage as skipped")
                Utils.markStageSkippedForConditional("${name}")
                return
            }
            node('docker_runner') {
                println("[${name}] Check out from version control")
                checkoutScm(pruneStaleBranch: true)

                Throwable tryError = null
                /* groovylint-disable-next-line NoDef, VariableTypeRequired */
                def dockerImage = docker.build(dockerTag, dockerBuildArgs)
                try {
                    dockerImage.inside {
                        if (installScript) {
                            println("[${name}] Running installScript")
                            sh label: 'Install RPMs',
                                script: "${installScript}"
                        }
                        if (buildScript) {
                            println("[${name}] Running buildScript")
                            sh label: 'Build deps',
                                script: "${buildScript}"
                        }
                        println("[${name}] Running stepMethod: ${stepMethod?.getClass()?.name}")
                        /* groovylint-disable-next-line NoDef, VariableTypeRequired */
                        def stepResult = stepMethod.call(stepMethodArgs)
                        jobStatusUpdate(jobStatus, name, stepResult)
                        if (valgrindSconsBuildArgs) {
                            println("[${name}] Running valgrind build for NLT")
                            // For non-release builds, create a separate build with the valgrind
                            // tag for NLT memcheck testing.  This is necessary to avoid problems
                            // caused by valgrind being confused by the Go runtime. We don't want
                            // to use the valgrind build for normal testing because it is much
                            // slower. BUILD_TYPE=dev is set for PR/dev builds in sconsArgs(), and
                            // TARGET_TYPE=release is used to select pre-built cached prerequisites.
                            jobStatusUpdate(jobStatus, name, sconsBuild(valgrindSconsBuildArgs))
                            sh label: 'Stash valgrind install tree for NLT',
                               script: 'tar -C / -cf opt-daos-valgrind.tar opt/daos'
                            stash(name: 'opt-daos-valgrind', includes: 'opt-daos-valgrind.tar')
                        }
                        if (generateRpmsScript) {
                            println("[${name}] Running generateRpmsScript")
                            sh label: 'Generate RPMs',
                                script: "${generateRpmsScript}"
                        }
                        if (buildRpmPostArgs) {
                            println("[${name}] Running buildRpmPost()")
                            buildRpmPost(buildRpmPostArgs)
                        }
                    }
                /* groovylint-disable-next-line CatchException */
                } catch (Exception e) {
                    tryError = e
                    println("[${name}] Caught exception in try: ${tryError}")
                    if (configLog) {
                        sh label: 'Archive config.log',
                           script: "if [ -f config.log ]; then mv config.log ${configLog}; fi"
                        archiveArtifacts artifacts: "${configLog}", allowEmptyArchive: true
                    }
                    jobStatusUpdate(jobStatus, name, 'FAILURE')
                    throw tryError
                } finally {
                    // Cleanup actions
                    try {
                        if (publishHtmlArgs) {
                            println("[${name}] Running publishHTML()")
                            publishHTML(publishHtmlArgs)
                        }
                        if (archiveArtifactsArgs) {
                            println("[${name}] Running archiveArtifacts()")
                            archiveArtifacts(archiveArtifactsArgs)
                        }
                        jobStatusUpdate(jobStatus, name)
                    /* groovylint-disable-next-line CatchException */
                    } catch (Exception finallyError) {
                        println("[${name}] Caught exception in finally: ${finallyError}")
                        /* groovylint-disable-next-line DuplicateStringLiteral */
                        jobStatusUpdate(jobStatus, name, 'FAILURE')
                        if (tryError == null) {
                            /* groovylint-disable-next-line ThrowExceptionFromFinallyBlock */
                            throw finallyError
                        }
                    }
                }
            }
            println("[${name}] Finished with ${jobStatus}")
        }
    }
}
