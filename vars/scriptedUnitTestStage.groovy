/* groovylint-disable NestedBlockDepth */
// vars/scriptedUnitTestStage.groovy

import org.jenkinsci.plugins.pipeline.modeldefinition.Utils

/**
 * scriptedUnitTestStage.groovy
 *
 * Get a unit test stage in scripted syntax.
 *
 * @param kwargs Map containing the following optional arguments (empty strings yield defaults):
 *      name                  test stage name
 *      runStage              whether or not to run the test stage
 *      label                 test stage default cluster label
 *      testBranch            if specified, checkout sources from this branch before running tests
 *      jobStatus             Map of status for each stage in the job/build
 *      distro                the distro to use for the test stage, e.g. 'el9'; defaults to ''
 *      timeoutTime           unitTest() timeout time in minutes; defaults to 60
 *      instRepos             unitTest() inst_repos argument; defaults to daosRepos()
 *      testScript            unitTest() test_script argument; defaults to ''
 *      withValgrind          unitTest() with_valgrind argument; defaults to ''
 *      alwaysScript          unitTest() always_script argument, e.g.
 *                              'ci/unit/test_nlt_post.sh'; defaults to ''
 *      testResults           unitTest() test_results argument; defaults to 'nlt-junit.xml'
 *      unstashOpt            unitTest() unstash_opt argument; defaults to true
 *      unstashTests          unitTest() unstash_tests argument; defaults to false
 *      instRpms              unitTest() inst_rpms argument; defaults to
 *                              unitPackages(target: distro) + ' daos-client-tests'
 *      imageVersion          unitTest() image_version argument, e.g 'el9.7'; defaults to ''
 *      provEnvVars           unitTest() prov_env_vars argument; defaults to ''
 *      unitTestPostArgs      Map of arguments to pass to unitTestPost() for the stage
 *      archiveArtifactsArgs  Map of arguments to pass to archiveArtifacts() for the stage
 * @return a scripted stage to run in a pipeline
 */
Map call(Map kwargs = [:]) {
    // General parameters
    String name = kwargs.get('name', '')
    Boolean runStage = kwargs.get('runStage', true) as Boolean
    String label = kwargs.get('label')
    String testBranch = kwargs.get('testBranch', '')
    Map jobStatus = kwargs.get('jobStatus', null) ?: [:]
    String distro = kwargs.get('distro', '')

    // Unit Test stage parameters
    Map unitTestArgs = kwargs.get('unitTestArgs', null) ?: [:]
    Map unitTestPostArgs = kwargs.get('unitTestPostArgs', null) ?: [:]
    Map archiveArtifactsArgs = kwargs.get('archiveArtifactsArgs', null) ?: [:]

    if (!name) {
        error("scriptedUnitTestStage() requires a stage 'name' argument")
    }

    // Add defaults for any missing unitTest() arguments
    if (!unitTestArgs.containsKey('inst_repos')) {
        // If inst_repos is not specified, use the default daosRepos() for the specified distro
        /* groovylint-disable-next-line DuplicateStringLiteral */
        unitTestArgs['inst_repos'] = daosRepos(distro)
    }
    if (!unitTestArgs.containsKey('inst_rpms')) {
        // If inst_rpms is not specified, use the default unitPackages() for the specified distro
        /* groovylint-disable-next-line DuplicateStringLiteral */
        unitTestArgs['inst_rpms'] = unitPackages(target: distro) + ' daos-client-tests'
    }

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
                    println("[${name}] Running unitTest() on ${label}")
                    jobStatusUpdate(jobStatus, name, unitTest(unitTestArgs))
                /* groovylint-disable-next-line CatchException */
                } catch (Exception e) {
                    tryError = e
                    println("[${name}] Caught exception in try: ${tryError}")
                    jobStatusUpdate(jobStatus, name, 'FAILURE')
                    throw tryError
                } finally {
                    try {
                        if (unitTestPostArgs) {
                            println("[${name}] Running unitTestPost()")
                            unitTestPost(unitTestPostArgs)
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
