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
   *                               Default 'Functional/*' + '/results.xml, ' +
   *                                       'Functional/*' + '/test-results/*' +
   *                                       '/data/*_results.xml'
   */

def call(Map config = [:]) {

  def myscript = "STAGE_NAME=" + env.STAGE_NAME + ' ' + "ci/functional/job_cleanup.sh"

  def always_script = config.get('always_script',
                                 myscript)
  def rc = sh label: "Job Cleanup",
           script: always_script,
           returnStatus: true

  def artifacts = config.get('artifacts', 'Functional/**')
  archiveArtifacts artifacts: artifacts

  def junit_results = config.get('testResults',
                                 'Functional/*/results.xml, ' +
                                 'Functional/*/test-results/*/data/*_results.xml')
  junit testResults: junit_results
}
