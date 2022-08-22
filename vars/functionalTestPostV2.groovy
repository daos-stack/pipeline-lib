/* groovylint-disable DuplicateStringLiteral, VariableName */
// vars/functionalTestPostV2.groovy

  /**
   * functionalTestPost step method
   *
   * @param config Map of parameters passed
   *
   * config['always_script']  Script to run after any test.
   *                          Default 'ci/functional/job_cleanup.sh'.
   *
   * config['artifacts']      Artifacts to archive.
   *                          Default env.STAGE_NAME + '/**'
   *
   * config['context']        Context name for SCM to identify the specific
   *                          stage to update status for.
   *                          Default is 'test/' + env.STAGE_NAME.
   *
   * config['description']    Description to report for SCM status.
   *                          Default env.STAGE_NAME.
   *
   * config['flow_name']      Stage Flow name for logging.
   *                          Default is env.STAGE_NAME.
   *                          For sh steps, the stage flow name is the label
   *                          assigned to the shell script.
   *
   * config['ignore_failure'] Whether a FAILURE result should post a failed step
   *
   * config['testResults']    Junit test result files.
   *                          Default env.STAGE_NAME subdirectories
   */

void call(Map config = [:]) {
    String always_script = config.get('always_script',
                                      'ci/functional/job_cleanup.sh')
    sh(label: 'Job Cleanup', script: always_script, returnStatus: true)

    String artifacts = stageStatusFilename() + ',' +
           config.get('artifacts', env.STAGE_NAME + '/**')
    archiveArtifacts(artifacts: artifacts)

    String junit_results = config.get('testResults',
                                      env.STAGE_NAME + '/*/*/results.xml, ' +
                                      env.STAGE_NAME + '/*/*/*/results.xml, ' +
                                      env.STAGE_NAME + '/*/framework_results.xml, ' +
                                      env.STAGE_NAME + '/*/*/test-results/*/data/*_results.xml, ' +
                                      env.STAGE_NAME + '/*/*/*/test-results/*/data/*_results.xml')

    String result_stash = 'result_for_' + sanitizedStageName()
    unstash name: result_stash
    int result_code = readFile(result_stash).toLong()
    String prev_result = currentBuild.result

    junit(testResults: junit_results)

    if (prev_result != currentBuild.result) {
        println('Junit or some other stage changed currentBuild result to ' +
                "${currentBuild.result}.")
    }

    // Save these in case we want to inspect them.
    archiveArtifacts(artifacts: junit_results)

    String status = 'SUCCESS'
    if (result_code != 0) {
        status = 'FAILURE'
    } else {
        status = checkJunitFiles(config)
    }

    String description = config.get('description', env.STAGE_NAME)
    String context = config.get('context', 'test/' + env.STAGE_NAME)
    String flow_name = config.get('flow_name', env.STAGE_NAME)
    boolean ignore_failure = config.get('ignore_failure', false)
    stepResult name: description,
               context: context,
               flow_name: flow_name,
               result: status,
               junit_files: junit_results,
               ignore_failure: ignore_failure

    sh(label: 'Install Launchable',
       script: 'pip3 install --user --upgrade launchable~=1.0')

    withCredentials([string(credentialsId: 'launchable-test', variable: 'LAUNCHABLE_TOKEN')]) {
        sh(label: 'Submit test results to Launchable',
           /* groovylint-disable-next-line GStringExpressionWithinString */
           script: 'if ls -l "' + env.STAGE_NAME + '''"/*/*/xunit1_results.xml 2>/dev/null; then
                        export PATH=$PATH:$HOME/.local/bin
                        launchable record tests --build ${BUILD_TAG//%2F/-} pytest ''' +
                                   '"' + env.STAGE_NAME + '''"/*/*/xunit1_results.xml
                    fi''')
    }
}
