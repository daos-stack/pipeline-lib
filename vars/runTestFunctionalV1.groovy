/* groovylint-disable DuplicateStringLiteral, GStringExpressionWithinString, LineLength, VariableName */
// vars/runTestFunctionalV1.groovy

/**
 * runTestFunctionalV1.groovy
 *
 * runTestFunctionalV1 pipeline step
 */

/* groovylint-disable-next-line MethodSize */
Map call(Map config = [:]) {
  /**
   * runTestFunctionalV1 step method
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

    String test_rpms = 'false'
    if (config['test_rpms'] == 'true') {
        test_rpms = 'true'
    }
    String functionalTestScript =
        '''ci/functional/run_test_functional_v1.sh "%s" "%s" "%s" "%s" "%s"'''

    config['script'] = String.format(functionalTestScript,
                                     config['pragma_suffix'],
                                     config['test_tag'],
                                     config['node_count'],
                                     config['ftest_arg'],
                                     test_rpms)
    config['junit_files'] = 'install/lib/daos/TESTING/ftest/avocado/job-results/job-*/*.xml install/lib/daos/TESTING/ftest/*_results.xml'
    config['failure_artifacts'] = 'Functional'

    if (test_rpms == 'true' && config['stashes']) {
        // we don't need (and might not even have) stashes if testing
        // from RPMs
        config.remove('stashes')
    }

    config.remove('pragma_suffix')
    config.remove('test_tag')
    config.remove('ftest_arg')
    config.remove('node_count')
    config.remove('test_rpms')

    return runTest(config)
}
