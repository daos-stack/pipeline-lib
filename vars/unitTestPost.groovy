// vars/unitTestPost.groovy

  /**
   * unitTestPost step method
   *
   * @param config Map of parameters passed
   *
   * config['always_script']       Script to run after any test.
   *                               Default 'ci/unit/test_post_always.sh'.
   *
   * TODO: Always provided, should make required
   * config['artifacts']           Artifacts to archive.
   *                               Default ['run_test.sh/*']
   *
   * config['ignore_failure']      Ignore test failures.  Default false.
   *
   * config['referenceJobName']    Reference job name.
   *                               Defaults to 'daos-stack/daos/master'
   *
   * config['testResults']         Junit test result files.
   *                               Default 'test_results/*.xml'
   *
   * config['valgrind_pattern']    Pattern for Valgind files.
   *                               Default: '*.memcheck.xml'
   *
   * config['valgrind_stash']      Name to stash valgrind artifacts
   *                               Required if more than one stage is
   *                               creating valgrind reports.
   */

// groovylint-disable DuplicateStringLiteral, MethodSize, VariableName
void call(Map config = [:]) {
    String always_script = config.get('always_script',
                                      'ci/unit/test_post_always.sh')
    Map stage_info = parseStageInfo(config)

    String context = config.get('context', 'test/' + env.STAGE_NAME)
    String description = config.get('description', env.STAGE_NAME)
    String flow_name = config.get('flow_name', env.STAGE_NAME)
    int zero = 0

    // Collect log and other result files from the test nodes
    sh label: 'Job Cleanup',
       script: always_script

    // Need to unstash the script result from runTest
    String result_stash = 'result_for_' + sanitizedStageName()
    unstash name: result_stash
    int resultCode = readFile(result_stash).toLong()
    string testResults = config.get('testResults', 'test_results/*.xml')
    String status = 'SUCCESS'

    // Need the ignore_failure setting the test was run with
    result_stash = 'ignore_failure_for_' + sanitizedStageName()
    unstash name: result_stash
    boolean ignore_failure_stash = readFile(result_stash).toBoolean()
    boolean ignore_failure = config.get('ignore_failure', ignore_failure_stash)

    // Need to pre-check the Valgrind files here also
    int vgfail = zero
    String vgrcs
    String target_dir = 'unit_test_memcheck_logs'
    String valgrind_pattern = stage_info.get('valgrind_pattern',
                                             'unit-test-*memcheck.xml')

    if (resultCode != zero) {
        status = 'FAILURE'
    } else {
        status = checkJunitFiles(testResults: testResults)
    }

    // Stash the valgrind files for later analysis
    if (config['valgrind_stash']) {
        stash name: config['valgrind_stash'], includes: valgrind_pattern
    }
    if (stage_info['with_valgrind']) {
        vgrcs = sh label: 'Check for Valgrind errors',
                   script: "grep -E '<error( |>)' ${valgrind_pattern} || true",
                   returnStdout: true
        if (vgrcs) {
            vgfail = 1
            status = 'FAILURE'
        }
        fileOperations([fileCopyOperation(excludes: '',
                                      flattenFiles: false,
                                      includes: valgrind_pattern,
                                      targetLocation: target_dir)])
        sh label: 'Create tarball of Valgrind xml files',
           script: "tar -czf ${target_dir}.tar.gz ${target_dir}"
    }
    if (config['testResults'] != 'None' ) {
        // groovylint-disable-next-line NoDouble
        double health_scale = 1.0
        if (ignore_failure) {
            health_scale = 0.0
        }
        junit testResults: testResults,
              healthScaleFactor: health_scale
    }
    if (stage_info['with_valgrind']) {
        String suite = sanitizedStageName()
        junitSimpleReport suite: suite,
                          file: suite + '_valgrind_results.xml',
                          errors: vgfail,
                          name: 'Valgrind_Memcheck',
                          class: 'Valgrind',
                          message: 'Valgrind Memcheck error detected',
                          testdata: vgrcs,
                          ignoreFailure: ignore_failure
    }
    stepResult name: description,
               context: context,
               flow_name: flow_name,
               result: status,
               junit_files: config.get('junit_files', 'test_results/*.xml'),
               ignore_failure: ignore_failure

    List artifact_list = config.get('artifacts', ['run_test.sh/*'])
    artifact_list.each { artifactPat ->
        println("Archiving Artifacts matching ${artifactPat}")
        archiveArtifacts artifacts: artifactPat,
                     allowEmptyArchive: ignore_failure
    }

    String target_stash = "${stage_info['target']}-${stage_info['compiler']}"
    if (stage_info['build_type']) {
        target_stash += '-' + stage_info['build_type']
    }

    // Coverage instrumented tests and Vagrind are probably mutually exclusive
    if (stage_info['compiler'] == 'covc') {
        return
    }

    if (stage_info['NLT']) {
        String cb_result = currentBuild.result
        discoverGitReferenceBuild(
          referenceJob: config.get('referenceJobName',
                                   'daos-stack/daos/master'),
          scm: 'daos-stack/daos')
        recordIssues enabledForFailure: true,
                     failOnError: !ignore_failure,
                     ignoreFailedBuilds: true,
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
                                   id: 'VM_test')

        if (cb_result != currentBuild.result) {
            println(
              "The recordIssues step changed result to ${currentBuild.result}.")
        }
    }
}
