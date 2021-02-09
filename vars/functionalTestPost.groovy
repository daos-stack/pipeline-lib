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
   *                               Default env.STAGE_NAME + '/**'
   *
   * config['testResults']         Junit test result files.
   *                               Default env.STAGE_NAME subdirectories
   */

def call(Map config = [:]) {

  def always_script = config.get('always_script',
                                 'ci/functional/job_cleanup.sh')
  def rc = sh label: "Job Cleanup",
           script: always_script,
           returnStatus: true

  def artifacts = config.get('artifacts', env.STAGE_NAME + '/**')
  archiveArtifacts artifacts: artifacts

  def junit_results = config.get('testResults',
                                 env.STAGE_NAME + '/*/*/results.xml, ' +
                                 env.STAGE_NAME + '/*/framework_results.xml, ' +
                                 env.STAGE_NAME + '/*/*/test-results/*/data/*_results.xml')
  junit testResults: junit_results
}
