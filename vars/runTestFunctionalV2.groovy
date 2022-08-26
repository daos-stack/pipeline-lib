/* groovylint-disable DuplicateStringLiteral, NestedBlockDepth, VariableName
   groovylint-disable CouldBeElvis */
// vars/runTestFunctionalV2.groovy

/**
 * runTestFunctionalV2.groovy
 *
 * runTestFunctionalV2 pipeline step
 */

void call(Map config = [:]) {
  /**
   * runTestFunctionalV2 step method
   *
   * @param config Map of parameters passed
   * @return None
   *
   * config['stashes'] Stashes from the build to unstash
   * config['ignore_failure'] Whether a FAILURE result should post a failed step
   * config['pragma_suffix'] The Test-tag pragma suffix
   * config['test_tag'] The test tags to run
   * config['ftest_arg'] An argument to ftest.sh
   * config['test_rpms'] Testing using RPMs, true/false
   *
   * config['context'] Context name for SCM to identify the specific stage to
   *                   update status for.
   *                   Default is 'test/' + env.STAGE_NAME.
   * config['failure_artifacts'] Artifacts to link to when test fails, if any.
                                 Default is env.STAGE_NAME.
   *
   *  Important:
   *     The SCM status checking for passing may expect a specific name.
   *
   *     Matrix stages must override this setting to include matrix axes
   *     names to ensure a unique name is generated.
   *
   *     Or the default name has to be changed in a way that is compatible
   *     with a future Matrix implementation.
   *
   * config['description']  Description to report for SCM status.
   *                        Default env.STAGE_NAME.
   */

    Map stage_info = parseStageInfo(config)

    if (!fileExists('ci/functional/test_main.sh')) {
        return runTestFunctionalV1(config)
    }

    Boolean test_rpms = false
    if (config['test_rpms'] == 'true') {
        test_rpms = true
    }
    config['script'] = 'TEST_TAG="' + config['test_tag'] + '" ' +
                       'FTEST_ARG="' + config['ftest_arg'] + '" ' +
                       'PRAGMA_SUFFIX="' + config['pragma_suffix'] + '" ' +
                       'NODE_COUNT="' + config['node_count'] + '" ' +
                       'OPERATIONS_EMAIL="' + env.OPERATIONS_EMAIL + '" ' +
                       "WITH_VALGRIND=${stage_info.get('with_valgrind', '')} " +
                       'ci/functional/test_main.sh'

    basedir = 'install/lib/daos/TESTING/ftest/avocado/job-results/'
    config['junit_files'] = "${basedir}job-*/*.xml " +
                            "${basedir}job-*/test-results/*/data/*_results.xml"
    if (!config['failure_artifacts']) {
        config['failure_artifacts'] = env.STAGE_NAME
    }

    if (test_rpms && config['stashes']){
        // we don't need (and might not even have) stashes if testing
        // from RPMs
        config.remove('stashes')
    }

    config.remove('pragma_suffix')
    config.remove('test_tag')
    config.remove('ftest_arg')
    config.remove('node_count')
    config.remove('test_rpms')

    // Notify SCM result in post steps.
    config['notify_result'] = false

    runTest(config)

    String covfile = 'test.cov'
    if (!fileExists('test.cov')) {
        covfile += '_not_done'
        fileOperations([fileCreateOperation(fileName: covfile,
                                            fileContent: '')])
    }
    String name = 'func' + stage_info['pragma_suffix'] + '-cov'
    stash name: config.get('coverage_stash', name),
          includes: covfile
}
