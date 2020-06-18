// vars/pythonBanditCheck.groovy

  /**
   * pythonBanditCheck step method
   *
   * @param config Map of parameters passed
   *
   * config['script']        Script to run bandit'.
   *                        Default 'ci/python_bandit_check.sh'.
   *
   * config['context']      Context name for SCM to identify the specific
   *                        stage to update status for.
   *                        Default is 'check/' + env.STAGE_NAME.
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
   *
   * config['junit_files']  Junit files to return.
   *                        Default 'bandit.xml'
   *
   * config['Unstable']     Convert build error to unstable.
   *                        default false.
   */

def call(Map config = [:]) {

  def context = config.get('context', 'check/' + env.STAGE_NAME)
  def description = config.get('description', env.STAGE_NAME)
  def bandit_script = config.get('script', 'ci/python_bandit_check.sh')

  Map stage_info = parseStageInfo(config)

  checkoutScm withSubmodules: true

  def error_stage_result = 'FAILURE'
  def error_build_result = 'FAILURE'
  if (config['unstable']) {
    error_stage_result = 'UNSTABLE'
    error_build_result = 'SUCCESS'
  }
  def bandit_junit = config.get('junit_files', 'bandit.xml')
  runTest script: bandit_script,
          junit_files: bandit_junit,
          ignore_failure: true
}
