/* groovylint-disable NestedBlockDepth */
// vars/scriptedTestRpmStage.groovy

import org.jenkinsci.plugins.pipeline.modeldefinition.Utils

/**
 * scriptedTestRpmStage.groovy
 *
 * Get a test stage in scripted syntax.
 *
 * @param kwargs Map containing the following optional arguments (empty strings yield defaults):
 *      name                  test stage name
 *      runStage              whether or not to run the test stage; defaults to true
 *      label                 test stage default cluster label
 *      testBranch            if specified, checkout sources from this branch before running tests;
 *                              defaults to ''
 *      jobStatus             Map of status for each stage in the job/build
 *      testRpmArgs           Map of arguments to pass to testRpm() for the stage
 *      alwaysScript          script to always run after the test stage, e.g.
 *                              'ci/rpm/test_daos_post.sh'; defaults to ''
 *      archiveArtifactsArgs  Map of arguments to pass to archiveArtifacts() for the stage
 *      distro                the distro to pass to daosRepos() if testRpmArgs does not specify a
 *                              inst_repos, e.g. 'el9'; defaults to null
 *      next_version          next daos package version to pass to daosPackagesVersion() if
 *                              testRpmArgs does not specify a daos_pkg_version; defaults to null
 * @return a scripted stage to run in a pipeline
 */
Map call(Map kwargs = [:]) {
    // General parameters
    String name = kwargs.get('name', '')
    Boolean runStage = kwargs.get('runStage', true) as Boolean
    String label = kwargs.get('label')
    String testBranch = kwargs.get('testBranch', '')
    Map jobStatus = kwargs.get('jobStatus', null) ?: [:]
    Map testRpmArgs = kwargs.get('testRpmArgs', null) ?: [:]
    String alwaysScript = kwargs.get('alwaysScript', '')
    Map archiveArtifactsArgs = kwargs.get('archiveArtifactsArgs', null) ?: [:]

    if (!name) {
        error("scriptedTestRpmStage() requires a stage 'name' argument")
    }

    return {
        stage("${name}") {
            if (!runStage) {
                println("[${name}] Stage skipped by runStage=false")
                Utils.markStageSkippedForConditional("${name}")
                return
            }

            // Add defaults for any missing testRpm() arguments
            if (!testRpmArgs.containsKey('inst_repos')) {
                /* groovylint-disable-next-line DuplicateStringLiteral */
                testRpmArgs['inst_repos'] = daosRepos(kwargs.get('distro', null))
            }
            if (!testRpmArgs.containsKey('daos_pkg_version')) {
                /* groovylint-disable-next-line DuplicateStringLiteral */
                testRpmArgs['daos_pkg_version'] = daosPackagesVersion(
                    kwargs.get('next_version', null))
            }

            node(label) {
                // Ensure access to any branch provisioning scripts exist
                if (testBranch) {
                    println("[${name}] Check out '${testBranch}' from version control")
                    checkoutScm(
                        url: 'https://github.com/daos-stack/daos.git',
                        branch: testBranch,
                        withSubmodules: false,
                        pruneStaleBranch: true)
                } else {
                    println("[${name}] Check out branch from version control")
                    checkoutScm(pruneStaleBranch: true)
                }

                Throwable tryError = null
                try {
                    println("[${name}] Running testRpm() on ${label}")
                    jobStatusUpdate(jobStatus, name, testRpm(testRpmArgs))
                /* groovylint-disable-next-line CatchException */
                } catch (Exception e) {
                    tryError = e
                    println("[${name}] Caught exception in try: ${tryError}")
                    jobStatusUpdate(jobStatus, name, 'FAILURE')
                    throw tryError
                } finally {
                    try {
                        if (alwaysScript) {
                            sh(script: alwaysScript,
                               label: "Running alwaysScript: ${alwaysScript}",
                               returnStatus: true)
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
