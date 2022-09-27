/* groovylint-disable DuplicateStringLiteral, DuplicateNumberLiteral
   groovylint-disable VariableName */
// vars/checkJunitFiles.groovy

  /**
   * checkJunitFiles step method
   *
   * Check Junit files for failures or errors detected.
   *
   * @param config Map of parameters passed
   *
   * config['junit_results']       Files passed to test expected to be
   *                               created.  Optional
   *
   * config['testResults']         Junit test result files.
   *                               Default env.STAGE_NAME subdirectories
   */

String call(Map config = [:]) {
    String junit_results = config.get('testResults',
                                      env.STAGE_NAME + '/*/*/results.xml, ' +
                                      env.STAGE_NAME + '/*/*/*/results.xml, ' +
                                      env.STAGE_NAME + '/*/framework_results.xml, ' +
                                      env.STAGE_NAME + '/*/*/test-results/*/data/*_results.xml, ' +
                                      env.STAGE_NAME + '/*/*/*/test-results/*/data/*_results.xml')

    // junit_files are space delimited, need to be converted.
    if (config['junit_files']) {
        junit_results += ',' + config['junit_files'].split().join(',')
    }

    String status = 'SUCCESS'
    if (!junit_results) {
        return status
    }

    boolean test_failure = false
    boolean test_error = false
    List filesList = []
    junit_results.split(',').each { junitfile ->
        filesList.addAll(findFiles(glob: junitfile.trim()))
    }
    if (!filesList) {
        return status
    }
    String junit_xml = filesList.collect { junitfile ->
        "'" + junitfile + "'"
    }.join(' ')
    if (sh(label: 'Check junit xml files for errors',
           script: 'grep -E "<error( |Details>|StackTrace>)" ' + junit_xml,
           returnStatus: true) == 0) {
        status = 'FAILURE'
        println 'Found at least one error in the Junit files.'
    } else if (sh(label: 'Check junit xml files for failures',
                  script: 'grep -E "<failure( |>)" ' + junit_xml,
                  returnStatus: true) == 0) {
        status = 'UNSTABLE'
        println 'Found at least one failure in the junit files.'
    }
    if (junit_xml.indexOf('pipeline-test-failure.xml') > -1) {
        test_failure = true
    } else if (junit_xml.indexOf('pipeline-test-error.xml') > -1) {
        test_error = true
    }
    // If we are testing this library, make sure the result is as expected
    if (test_failure || test_error) {
        String expected_status
        if (test_failure) {
            expected_status = 'UNSTABLE'
        } else if (test_error) {
            expected_status = 'FAILURE'
        }
        if (status == expected_status) {
            echo "Expected status ${status} found"
            status = 'SUCCESS'
        } else {
            // and fail the step if it's not
            echo "Expected status ${expected_status} not found.  status == ${status}"
            status = 'UNSTABLE'
        }
    }
    return status
}
