// vars/functionalTestPostV2.groovy

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
   *
   * config['valgrind_pattern']    Pattern for Valgind files.
   *                               Default: '*.memcheck.xml'
   *
   * config['valgrind_stash']      Name to stash valgrind artifacts
   *                               Required if more than one stage is
   *                               creating valgrind reports.
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
                                   env.STAGE_NAME + '/*/*/*/results.xml, ' +
                                   env.STAGE_NAME + '/*/framework_results.xml, ' +
                                   env.STAGE_NAME + '/*/*/test-results/*/data/*_results.xml, ' +
                                   env.STAGE_NAME + '/*/*/*/test-results/*/data/*_results.xml')

    junit testResults: junit_results

    if (config['valgrind_stash']) {
        def valgrind_pattern = config.get('valgrind_pattern', 'valgrind.*.memcheck-checked')
        stash name: config['valgrind_stash'], includes: valgrind_pattern
    }

}
