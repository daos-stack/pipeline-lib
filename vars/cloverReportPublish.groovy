/* groovylint-disable DuplicateStringLiteral, DuplicateNumberLiteral
   groovylint-disable NestedBlockDepth, VariableName */
// vars/cloverReportPublish.groovy

  /**
   * clover Report Publish step method
   *
   * Consolidiate clover data for publising.
   *
   * @param config Map of parameters passed
   *
   * config['coverage_healthy']    Map of a coverage healthy target.
   *                               Default is:
   *                                 [method:70, conditional:80, statement:80]
   *
   * config['coverage_script']     Script to run to get coverage results
   *                               Default is "ci/bullseye_generate_report.sh"
   *
   * config['coverage_stashes']    list of stashes for coverage reports.
   *                               Required.  Each stash must contain one
   *                               file named test.cov.
   *
   * Config['coverage_website']    Zip file to contain the resulting
   *                               coverage website, if it is a coverage
   *                               build.  Defaults to 'coverage_website.zip'
   *
   * config['ignore_failure']      Ignore test failures.  Default false.
   *
   * config['stash']               Stash name for the ".build-vars.*" files.
   */

void call(Map config = [:]) {
    // If we don't have a BULLSEYE environment variable set
    // there are no Bullsye reports to process.
    if (!env.BULLSEYE) {
        return
    }
    Map stage_info = parseStageInfo(config)

    String coverage_website = config.get('coverage_website',
                                         'coverage_website.zip')

    String url_base = env.JENKINS_URL +
                      'job/daos-stack/job/tools/job/master' +
                      '/lastSuccessfulBuild/artifact/'

    httpRequest url: url_base + 'bullshtml.jar',
                httpMode: 'GET',
                outputFile: 'bullshtml.jar'

    List stashes = []
    if (config['coverage_stashes']) {
        stashes = config['coverage_stashes']
    } else {
        error 'No coverage_stashes passed!'
    }

    String target_stash = "${stage_info['target']}-${stage_info['compiler']}"
    if (stage_info['build_type']) {
        target_stash += '-' + stage_info['build_type']
    }

    unstash config.get('stash', "${target_stash}-build-vars")

    int stash_cnt = 0
    stashes.each { name ->
        try {
            unstash name
            // The functional tests may or may not produce a test.cov
            // depending on the run options.
            if (fileExists('test.cov')) {
                stash_cnt++
                String new_name = "test.cov_${stash_cnt}"
                fileOperations([fileRenameOperation(source: 'test.cov',
                                                destination: new_name)])
          }
        } catch (hudson.AbortException ex) {
            println("Unstash failed: ${ex}")
        }
    }

    sh label: 'Create Coverage Report',
       script: config.get('coverage_script', 'ci/bullseye_generate_report.sh')

    String cb_result = currentBuild.result
    step([$class: 'CloverPublisher',
          cloverReportDir: 'test_coverage',
          cloverReportFileName: 'clover.xml',
          healthyTarget: config.get('coverage_healthy',
                                     [methodCoverage: 70,
                                      conditionalCoverage: 80,
                                      statementCoverage: 80])])

    if (cb_result != currentBuild.result) {
        println('The CloverPublisher plugin changed result to ' +
                "${currentBuild.result}.")
    }

    sh label: 'Create test coverage Tarball',
        script: """rm -f ${coverage_website}
                   if [ -d 'test_coverage' ]; then
                     zip -q -r -9 ${coverage_website} test_coverage
                   fi"""
    archiveArtifacts artifacts: coverage_website,
                     allowEmptyArchive: true
}
