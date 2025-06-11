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

    String junit_results = config.get('testResults',
                                      env.STAGE_NAME + '/*/*/results.xml, ' +
                                      env.STAGE_NAME + '/*/*/*/results.xml, ' +
                                      env.STAGE_NAME + '/*/framework_results.xml, ' +
                                      env.STAGE_NAME + '/*/*/test-results/*/data/*_results.xml, ' +
                                      env.STAGE_NAME + '/*/*/*/test-results/*/data/*_results.xml')

    // Need to unstash the script result from runTest
    String results_map = 'results_map_' + sanitizedStageName()
    unstash name: results_map
    Map results = readYaml file: results_map
    String prev_result = currentBuild.result

    junit(testResults: junit_results)

    if (prev_result != currentBuild.result) {
        println('Junit or some other stage changed currentBuild result to ' +
                "${currentBuild.result}.")
    }

    // Save these in case we want to inspect them.
    archiveArtifacts(artifacts: junit_results)

    if (results['result_code'] != 0) {
        results['result'] = 'FAILURE'
    } else {
        results['result'] = checkJunitFiles(config)
    }

    // Look for a hardware failure
    String hardwareJunit = "${env.STAGE_NAME}/hardware_prep/*/results.xml"
    String hardwareResult = checkJunitFiles(testResults: hardwareJunit)
    if (hardwareResult == 'FAILURE') {
        buildAgentControl(action: 'offline',
                          message: 'Hardware test detected an error',
                          subject: 'CI Test failure - Hardware issue.')
    }

    String description = config.get('description', env.STAGE_NAME)
    String context = config.get('context', 'test/' + env.STAGE_NAME)
    String flow_name = config.get('flow_name', env.STAGE_NAME)
    boolean ignore_failure = config.get('ignore_failure', false)
    stepResult name: description,
               context: context,
               flow_name: flow_name,
               result: results['result'],
               junit_files: junit_results,
               ignore_failure: ignore_failure

    String artifacts = stageStatusFilename() + ',' +
           config.get('artifacts', env.STAGE_NAME + '/**')
    archiveArtifacts(artifacts: artifacts)

    // Analyze test failures
    String jobName = env.JOB_NAME.replace('/', '_')
    jobName += '_' + env.BUILD_NUMBER
    String fileName = env.DAOS_STACK_JOB_STATUS_DIR + '/' + jobName

    if (fileExists('ci/functional/launchable_analysis')) {
        sh(label: 'Analyze failed tests vs. Launchable subset',
           script: 'ci/functional/launchable_analysis "' + fileName + '"')
    }

    String https_proxy = ''
    if (env.DAOS_HTTPS_PROXY) {
        https_proxy = "${env.DAOS_HTTPS_PROXY}"
    } else if (env.HTTPS_PROXY) {
        https_proxy = "${env.HTTPS_PROXY}"
    }
    String script = 'if pip3 install'
    if (https_proxy) {
        script += ' --proxy "' + https_proxy + '"'
    }
    script += ' --user --upgrade launchable~=1.0'


    sh(label: 'Install Launchable',
       script: script)

    try {
        withCredentials([string(credentialsId: 'launchable-test', variable: 'LAUNCHABLE_TOKEN')]) {
            sh(label: 'Submit test results to Launchable',
            /* groovylint-disable-next-line GStringExpressionWithinString */
            script: 'if ls -l "' + env.STAGE_NAME + '''"/*/*/xunit1_results.xml 2>/dev/null; then
                            export PATH=$PATH:$HOME/.local/bin
                            launchable record tests --build ${BUILD_TAG//%2F/-} pytest ''' +
                                    '"' + env.STAGE_NAME + '''"/*/*/xunit1_results.xml
                        fi''')
            }
    /* groovylint-disable-next-line CatchException */
    } catch (Exception error) {
        println(
            "Ignoring failure to record " + env.STAGE_NAME + " tests with launchable: " +
            error.getMessage())
    }
    if (!ignore_failure && results['result'] == 'FAILURE') {
        unstable "Failure detected with test harness or hardware."
    }
}
