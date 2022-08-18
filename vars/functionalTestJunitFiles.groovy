/* groovylint-disable DuplicateStringLiteral, VariableName */
// vars/functionalTestJunitFiles.groovy

  /**
   * functionalTestJunitFiles step method
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

    String xml_files = sh(label: 'debug xml files',
                          script: '''#!/bin/bash
                                     hostname -s
                                     ls /usr/bin/find* || true
                                     find . -name '*.xml' || true
                                     ''',
                          returnStdout: true)
    println("DEBUG xml files found = -${xml_files}-")

    println("DEBUG junit_results=-${junit_results}-")
    return junit_results
}
