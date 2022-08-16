/* groovylint-disable DuplicateStringLiteral, VariableName */
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
   */

void call(Map config = [:]) {
    String always_script = config.get('always_script',
                                      'ci/functional/job_cleanup.sh')
    sh(label: 'Job Cleanup', script: always_script, returnStatus: true)

    String artifacts = stageStatusFilename() + ',' +
           config.get('artifacts', env.STAGE_NAME + '/**')
    archiveArtifacts(artifacts: artifacts)

    String junit_results = FunctionalTestUnitFiles(config)

    junit(testResults: junit_results)

    // Save these temporarily
    archiveArtifacts(artifacts: junit_results)

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
