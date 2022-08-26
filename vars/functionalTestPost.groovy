/* groovylint-disable DuplicateStringLiteral, CouldBeElvis */
// vars/functionalTestPost.groovy

  /**
   * functionalTestPost step method
   *
   * @param config Map of parameters passed
   *
   * config['always_script']  Script to run after any test.
   *                          Default 'ci/functional/job_cleanup.sh'.
   *
   * config['artifacts']      Artifacts to archive.
   *                          Default env.STAGE_NAME + '/**'
   *
   * config['context']        Context name for SCM to identify the specific
   *                          stage to update status for.
   *                          Default is 'test/' + env.STAGE_NAME.
   *
   * config['description']    Description to report for SCM status.
   *                          Default env.STAGE_NAME.
   *
   * config['flow_name']      Stage Flow name for logging.
   *                          Default is env.STAGE_NAME.
   *                          For sh steps, the stage flow name is the label
   *                          assigned to the shell script.
   *
   * config['ignore_failure'] Whether a FAILURE result should post a failed step
   *
   * config['testResults']    Junit test result files.
   *                          Default env.STAGE_NAME subdirectories
   */

void call(Map config = [:]) {
    if (!config['artifacts']) {
        config['artifacts'] = 'Functional/**'
    }
    if (!config['testResults']) {
        config['testResults'] = 'Functional/*/results.xml, ' +
                                'Functional/*/framework_results.xml, ' +
                                'Functional/*/test-results/*/data/*_results.xml'
    }
    functionalTestPostV2(config)
}
