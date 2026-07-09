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
    String name = kwargs.get('name', 'Unknown Unit Test Stage')
    Boolean runStage = kwargs.get('runStage', true) as Boolean
    String label = kwargs.get('label')
    String testBranch = kwargs.get('testBranch', '')
    Map jobStatus = kwargs.get('jobStatus', null) ?: [:]
    String distro = kwargs.get('distro', '')

    // Unit Test stage parameters
    Map unitTestArgs = [
        'timeout_time': kwargs.get('timeoutTime', 60),
        'inst_repos': kwargs.get('instRepos', daosRepos(distro)),
        'test_script': kwargs.get('testScript', ''),
        'with_valgrind': kwargs.get('withValgrind', ''),
        'always_script': kwargs.get('alwaysScript', ''),
        'test_results': kwargs.get('testResults', 'nlt-junit.xml'),
        'unstash_opt': kwargs.get('unstashOpt', true),
        'unstash_tests': kwargs.get('unstashTests', false),
        'inst_rpms': kwargs.get('instRpms', unitPackages(target: distro) + ' daos-client-tests'),
        'image_version': kwargs.get('imageVersion', ''),
        'prov_env_vars': kwargs.get('provEnvVars', '')
    ]
    Map unitTestPostArgs = kwargs.get('unitTestPostArgs', null) ?: [:]
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

                try {
                    println("[${name}] Running unitTest() on ${label}")
                    jobStatusUpdate(jobStatus, name, unitTest(unitTestArgs))
                } catch (Exception e) {
                    println("[${name}] Caught exception in try: ${e}")
                    jobStatusUpdate(jobStatus, name, 'FAILURE')
                    throw e
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
