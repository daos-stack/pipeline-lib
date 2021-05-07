// vars/runTestFunctional.groovy

/**
 * runTestFunctional.groovy
 *
 * runTestFunctional pipeline step
 *
 */

void call(Map config = [:]) {
  /**
   * runTestFunctional step method
   *
   * @param config Map of parameters passed
   * @return None
   *
   * config['stashes'] Stashes from the build to unstash
   * config['ignore_failure'] Whether a FAILURE result should post a failed step
   * config['pragma_suffix'] The Test-tag pragma suffix
   * config['test_tag'] The test tags to run
   * config['ftest_arg'] An argument to ftest.sh
   * config['test_repeat'] Number of times to repeat a functional test
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

    config['failure_artifacts'] = 'Functional'

    runTestFunctionalV2(config)

}
