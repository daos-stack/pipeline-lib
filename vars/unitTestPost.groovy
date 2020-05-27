// vars/unitTestPost.groovy

  /**
   * unitTestPost step method
   *
   * @param config Map of parameters passed
   *
   * config['always_script']       Script to run after any test.
   *                               Default 'ci/unit/test_post_always.sh'.
   *
   * config['artifacts']           Artifacts to archive.
   *                               Default ['run_test.sh/*', 'vm_test/**']
   *
   * config['testResults']         Junit test result files.
   *                               Default 'test_results/*.xml'
   */

def call(Map config = [:]) {
  // TODO: need to watch for https://issues.jenkins-ci.org/browse/JENKINS-58952
  // where label on sh commmands in a post section needs to be after the
  // script parameter.  May not affect groovy library.

  def always_script = config.get('always_script',
                                 'ci/unit/test_post_always.sh')
  sh script: always_script,
     label: "Job Cleanup"

  Map stage_info = parseStageInfo(config)
  if (stage_info['compiler'] == 'covc' ) {
    // Special Bullseye handling
    // Only produce Bullseye/clover artifacts
    step([$class: 'CloverPublisher',
         cloverReportDir: 'test_coverage',
         cloverReportFileName: 'clover.xml'])
    sh label: 'Create test coverage Tarball',
       script: '''rm -f coverage_website.zip
                  if [ -d 'test_coverage' ]; then
                    zip -r -9 coverage_website.zip test_coverage
                  fi'''
    archiveArtifacts artifacts: coverage_website.zip,
                     allowEmptyArchive: true
    return
  }

  junit testResults: config.get('testResults', 'test_results/*.xml')

  def artifact_list = config.get('artifacts', ['run_test.sh/*', 'vm_test/**'])
  artifact_list.each {
    archiveArtifacts artifacts: it
  }

  publishValgrind failBuildOnInvalidReports: true,
                  failBuildOnMissingReports: true,
                  failThresholdDefinitelyLost: '0',
                  failThresholdInvalidReadWrite: '0',
                  failThresholdTotal: '0',
                  pattern: 'dnt.*.memcheck.xml',
                  publishResultsForAbortedBuilds: false,
                  publishResultsForFailedBuilds: true,
                  sourceSubstitutionPaths: '',
                  unstableThresholdDefinitelyLost: '0',
                  unstableThresholdInvalidReadWrite: '0',
                  unstableThresholdTotal: '0'

  recordIssues enabledForFailure: true,
               failOnError: true,
               referenceJobName: 'daos-stack/daos/master',
               ignoreFailedBuilds: false,
               ignoreQualityGate: true,
               // Set qualitygate to 1 new "NORMAL" priority message
               // Supporting messages to help identify causes of
               // problems are set to "LOW", and there are a
               // number of intermittent issues during server
               // shutdown that would normally be NORMAL but in
               // order to have stable results are set to LOW.

               qualityGates: [
                 [threshold: 1, type: 'TOTAL_HIGH', unstable: true],
                 [threshold: 1, type: 'TOTAL_ERROR', unstable: true],
                 [threshold: 1, type: 'NEW_NORMAL', unstable: true]],
                name: "Node local testing",
                tool: issues(pattern: 'vm_test/nlt-errors.json',
                             name: 'NLT results',
                             id: 'VM_test')
}
