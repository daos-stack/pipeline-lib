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
 *          stepMethod              method to call to run the stage
 *          archiveArtifactsArgs    optional arguments to pass to archiveArtifacts()
 * @return a scripted stage to run in a pipeline
 */
/* groovylint-disable-next-line MethodSize */
Map call(Map kwargs = [:]) {
    String name = kwargs.get('name', null)
    Boolean runStage = kwargs.get('runStage', true)
    Map jobStatus = kwargs.get('jobStatus', null) ?: [:]
    String dockerTag = kwargs.get('dockerTag', null)
    String dockerBuildArgs = kwargs.get('dockerBuildArgs', '')
    Closure stepMethod = kwargs.get('stepMethod')
    Map archiveArtifactsArgs = kwargs.get('archiveArtifactsArgs', null) ?: [:]

    if (!name) {
        error("scriptedDockerStage() requires a stage 'name' argument")
    }
    if (!dockerTag) {
        error("scriptedDockerStage() requires a 'dockerTag' argument")
    }

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

                Throwable tryError = null
                /* groovylint-disable-next-line NoDef, VariableTypeRequired */
                def dockerImage = docker.build(dockerTag, dockerBuildArgs)
                try {
                    dockerImage.inside {
                        println("[${name}] Running stepMethod: ${stepMethod?.getClass()?.name}")
                        jobStatusUpdate(jobStatus, name, stepMethod.call())
                    }
                /* groovylint-disable-next-line CatchException */
                } catch (Exception e) {
                    tryError = e
                    println("[${name}] Caught exception in try: ${tryError}")
                    jobStatusUpdate(jobStatus, name, 'FAILURE')
                    throw tryError
                } finally {
                    // Cleanup actions
                    try {
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
