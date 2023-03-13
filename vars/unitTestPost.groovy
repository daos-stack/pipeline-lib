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

// groovylint-disable DuplicateNumberLiteral, DuplicateStringLiteral,
// groovylint-disable VariableName

// groovylint-disable-next-line MethodSize
void call(Map config = [:]) {
    String always_script = config.get('always_script',
                                      'ci/unit/test_post_always.sh')
    sh label: 'Job Cleanup',
       script: always_script

    Map stage_info = parseStageInfo(config)

    if (config['testResults'] != 'None' ) {
        // groovylint-disable-next-line NoDouble
        double health_scale = 1.0
        if (config['ignore_failure']) {
            health_scale = 0.0
        }
        String cb_result = currentBuild.result
        junit testResults: config.get('testResults', 'test_results/*.xml'),
              healthScaleFactor: health_scale
        if (cb_result != currentBuild.result) {
            println "The junit plugin changed result to ${currentBuild.result}."
        }
    }
    if (stage_info['with_valgrind']) {
        String target_dir = 'unit_test_memcheck_logs'
        String src_files = 'unit-test-*.memcheck.xml'
        int vgfail = 0
        int vgerr = 0
        String rcs = sh label: 'Check for Valgrind errors',
                 script: "grep -E '<error( |>)' ${src_files} || true",
                 returnStdout: true
        if (rcs) {
            vgfail = 1
        }
        String suite = sanitizedStageName()
        junitSimpleReport suite: suite,
                          file: suite + '_valgrind_results.xml',
                          fails: vgfail,
                          errors: vgerr,
                          name: 'Valgrind_Memcheck',
                          class: 'Valgrind',
                          message: 'Valgrind detected',
                          testdata: rcs
        fileOperations([fileCopyOperation(excludes: '',
                                      flattenFiles: false,
                                      includes: src_files,
                                      targetLocation: target_dir)])
        sh label: 'Create tarball of *.memcheck.xml files',
           script: "tar -czf ${target_dir}.tar.gz ${target_dir}"
    }

    List artifact_list = config.get('artifacts', ['run_test.sh/*'])
    boolean ignore_failure = config.get('ignore_failure', false)
    artifact_list.each { artifactPat ->
        println("Archiving artifacts matching: ${artifactPat}")
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

    if (config['valgrind_stash']) {
        String valgrind_pattern = config.get('valgrind_pattern',
                                             '*memcheck.xml')
        stash name: config['valgrind_stash'], includes: valgrind_pattern
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
