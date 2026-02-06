// vars/pythonBanditCheck.groovy

  /**
   * pythonBanditCheck step method
   *
   * @param config Map of parameters passed
   *
   * config['script']        Script to run bandit'.
   *                        Default 'ci/python_bandit_check.sh'.
   *
   * config['junit_files']  Junit files to return.
   *                        Default 'bandit.xml'
   */

Map call(Map config = [:]) {
    String banditScript = config.get('script', 'ci/python_bandit_check.sh')

    checkoutScm withSubmodules: true

    String banditJunit = config.get('junit_files', 'bandit.xml')
    return runTest(script: banditScript,
                   junit_files: banditJunit,
                   ignore_failure: true)
}
