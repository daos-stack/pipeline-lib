/* groovylint-disable DuplicateNumberLiteral */
// vars/unitTestPost.groovy

  /**
   * unitTestPost step method
   *
   * @param config Map of parameters passed
   *
   * TODO: Always provided, should make required
   * config['artifacts']           Artifacts to archive.
   *                               Default ['run_test.sh/*']
   *
   * config['referenceJobName']    Reference job name.
   *                               Defaults to 'daos-stack/daos/master'
   *
   * config['testResults']         Junit test result files.
   *                               Default 'test_results/*.xml'
   *
   * config['valgrind_pattern']    Pattern for Valgrind files.
   *                               Default: '*.memcheck.xml'
   *
   * config['valgrind_stash']      Name to stash Valgrind artifacts
   *                               Required if more than one stage is
   *                               creating Valgrind reports.
   */

// groovylint-disable DuplicateStringLiteral, VariableName
/* groovylint-disable-next-line MethodSize */
void call(Map config = [:]) {
    Map stage_info = parseStageInfo(config)
    String cbcResult = currentBuild.currentResult

    // Stash the Valgrind files for later analysis
    String valgrind_pattern = stage_info.get('valgrind_pattern',
                                             'unit-test-*memcheck.xml')
    if (config['valgrind_stash']) {
        try {
            stash name: config['valgrind_stash'], includes: valgrind_pattern
        } catch (hudson.AbortException e) {
            println('Failed to stash Valgrind files with pattern ' +
                    "${valgrind_pattern}: ${e.message}")
        }
    }
    String context = config.get('context', 'test/' + env.STAGE_NAME)
    String description = config.get('description', env.STAGE_NAME)
    String flow_name = config.get('flow_name', env.STAGE_NAME)
    // Need to unstash the script result from runTest
    String results_map = 'results_map_' + sanitizedStageName()
    println("We are trying to unstash ${results_map}")
    Map results = [:]
    try {
        unstash name: results_map
        results = readYaml file: results_map
    } catch (hudson.AbortException e) {
        println("Failed to unstash ${results_map}: ${e.message}")
    }

    List artifact_list = config.get('artifacts', ['run_test.sh/*'])

    String testResults = stage_info.get('testResults', 'test_results/*.xml')
    if (testResults != 'None' ) {
        // groovylint-disable-next-line NoDouble
        double health_scale = 1.0
        if (results['ignore_failure']) {
            health_scale = 0.0
        }
        junit testResults: testResults,
              healthScaleFactor: health_scale
    }
    if (stage_info['with_valgrind'] || stage_info['NLT']) {
        String suite = sanitizedStageName()
        int vgfail = 0
        String testdata
        if (results.containsKey('valgrind_check')) {
            vgfail = 1
            testdata = results['valgrind_check']
        }
        junitSimpleReport suite: suite,
                          file: suite + '_valgrind_results.xml',
                          errors: vgfail,
                          name: 'Valgrind_Memcheck',
                          class: 'Valgrind',
                          message: 'Valgrind Memcheck error detected',
                          testdata: testdata,
                          ignoreFailure: results['ignore_failure']
        String memcheck = sanitizedStageName() + '_memcheck_results.tar.bz2'
        artifact_list.add(memcheck)
    }
    stepResult name: description,
               context: context,
               flow_name: flow_name,
               result: results['result'],
               junit_files: testResults,
               ignore_failure: results['ignore_failure']

    artifact_list.each { artifactPat ->
        println("Archiving Artifacts matching ${artifactPat}")
        archiveArtifacts artifacts: artifactPat,
                     allowEmptyArchive: results['ignore_failure']
    }
    String target_stash = "${stage_info['target']}-${stage_info['compiler']}"
    if (stage_info['build_type']) {
        target_stash += '-' + stage_info['build_type']
    }
    // Coverage instrumented tests and Valgrind are probably mutually exclusive
    if (stage_info['compiler'] == 'covc') {
        return
    }

    if (stage_info['NLT']) {
        String cb_result = currentBuild.result
        discoverGitReferenceBuild(referenceJob: config.get('referenceJobName',
                                                           'daos-stack/daos/master'),
                                  scm: 'daos-stack/daos',
                                  requiredResult: 'UNSTABLE')
        recordIssues enabledForFailure: true,
                     /* ignore warning/errors from PMDK logging system */
                     filters: [excludeFile('pmdk/.+')],
                     failOnError: !results['ignore_failure'],
                     ignoreQualityGate: true,
                     // Set qualitygate to 1 new "NORMAL" priority message
                     // Supporting messages to help identify causes of
                     // problems are set to "LOW".
                     qualityGates: [
                       [threshold: 1, type: 'TOTAL_ERROR'],
                       [threshold: 1, type: 'TOTAL_HIGH'],
                       [threshold: 1, type: 'NEW_NORMAL', unstable: true],
                       [threshold: 1, type: 'NEW_LOW', unstable: true]],
                     name: 'Node local testing',
                     tool: issues(pattern: 'vm_test/nlt-errors.json',
                                  name: 'NLT results',
                                  id: 'VM_test'),
                     scm: 'daos-stack/daos'

        if (cb_result != currentBuild.result) {
            println(
              "The recordIssues step changed result to ${currentBuild.result}.")
        }
    }
    if (cbcResult != currentBuild.currentResult &&
        currentBuild.resultIsWorseOrEqualTo('FAILURE')) {
        error 'unitTestPost detected a failure'
    }

    println results['result_code'].getClass()
    println results['ignore_failure'].getClass()

    if (results['result_code'] != 0  && !results['ignore_failure']) {
        // Extra information for when this happens.
        println("results: ${results}")
        println results['result_code'].getClass()
        println results['ignore_failure'].getClass()
        error 'Failure detected in test run!'
    }
}
