// vars/functionalTestPost.groovy

  /**
   * functionalTestPost step method
   *
   * @param config Map of parameters passed
   *
   * config['always_script']       Script to run after any test.
   *                               Default 'ci/functional/job_cleanup.sh'.
   *
   * config['artifacts']           Artifacts to archive.
   *                               Default 'Functional/**'
   *
   * config['testResults']         Junit test result files.
   *                               Default env.STAGE_NAME subdirectories
   */

def call(Map config = [:]) {

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
