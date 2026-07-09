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
 *      runStage              whether or not to run the test stage
 *      label                 test stage default cluster label
 *      testBranch            if specified, checkout sources from this branch before running tests
 *      jobStatus             Map of status for each stage in the job/build
 *      imageVersion          testRpm() target argument; defaults to ''
 *      instRepos             testRpm() inst_repos argument; defaults to daosRepos()
 *      daosPkgVersion        testRpm() daos_pkg_version argument; defaults to
 *                              daosPackagesVersion(next_version())
 *      instRpms              testRpm() inst_rpms argument; defaults to ''
 *      ignoreFailure         testRpm() ignore_failure argument; defaults to false
 *      alwaysScript          script to run always after the test stage, e.g.
 *                              'ci/rpm/test_daos_post.sh'; defaults to ''.
 *      archiveArtifactsArgs  Map of arguments to pass to archiveArtifacts() for the stage
 * @return a scripted stage to run in a pipeline
 */
Map call(Map kwargs = [:]) {
    // General parameters
    String name = kwargs.get('name', 'Unknown Test RPM Stage')
    Boolean runStage = kwargs.get('runStage', true) as Boolean
    String label = kwargs.get('label')
    String testBranch = kwargs.get('testBranch', '')
    Map jobStatus = kwargs.get('jobStatus', null) ?: [:]
    Map testRpmArgs = [
        target: kwargs.get('imageVersion', ''),
        inst_repos: kwargs.get('instRepos', daosRepos()),
        daos_pkg_version: kwargs.get(
            'daosPkgVersion', daosPackagesVersion(kwargs.get('next_version', null))),
        inst_rpms: kwargs.get('instRpms', ''),
        ignore_failure: kwargs.get('ignoreFailure', false)
    ]
    String alwaysScript = kwargs.get('alwaysScript', '')
    Map archiveArtifactsArgs = kwargs.get('archiveArtifactsArgs', null) ?: [:]

    return {
        stage("${name}") {
            if (!runStage) {
                println("[${name}] Stage skipped by runStage=false")
                Utils.markStageSkippedForConditional("${name}")
                return
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
                            sh(script: alwaysScript, label: "Running alwaysScript: ${alwaysScript}")
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
