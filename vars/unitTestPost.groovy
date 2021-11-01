// vars/unitTestPost.groovy

  /**
   * unitTestPost step method
   *
   * @param config Map of parameters passed
   *
   * config['always_script']       Script to run after any test.
   *                               Default 'ci/unit/test_post_always.sh'.
   *
   * config['skip_post_script']    Skip the running of always_script.
   *                               Default false.
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
   *
   */

def call(Map config = [:]) {

  String always_script = config.get('always_script',
                                        'ci/unit/test_post_always.sh')

  ////////////////////////////////////////////////////////////////////////////////
  println "TRACE: unitTestPost\n"
  println "TRACE: config['skip_post_script']" + config['skip_post_script'] + "\n"

  echo "TRACE: find \"config['testResults']\"\n"
  sh "find \"" + config['testResults'] + "\" || :"
  echo "TRACE: find .\n"
  sh "find ."
  echo "TRACE: pwd\n"
  sh "pwd"
  ////////////////////////////////////////////////////////////////////////////////

  if (!config['skip_post_script']) {
    sh label: 'Job Cleanup',
       script: always_script
  }

  Map stage_info = parseStageInfo(config)

  if (config['testResults'] != 'None' ) {
    println "TRACE: unitTestPost.groovy:60\n"
    double health_scale = 1.0
    if (config['ignore_failure']) {
      println "TRACE: unitTestPost.groovy:63\n"
      health_scale = 0.0
    }

    def cb_result = currentBuild.result

    ////////////////////////////////////////////////////////////////////////////////
    println "TRACE: cb_result " + cb_result + "\n"
    ////////////////////////////////////////////////////////////////////////////////

    junit testResults: config.get('testResults', 'test_results/*.xml'),
          healthScaleFactor: health_scale

    if (cb_result != currentBuild.result) {
      println "TRACE: unitTestPost.groovy:77\n"
      println "The junit plugin changed result to ${currentBuild.result}."
    }
  }

  if (stage_info['with_valgrind']) {

    println "TRACE: unitTestPost.groovy:84\n"
    println "TRACE: stage_info['NLT']" + stage_info['NLT'] + "\n"
    println "TRACE: stage_info['test']" + stage_info['test'] + "\n"
    println "TRACE: stage_info['target']" + stage_info['target'] + "\n"
    println "TRACE: stage_info['compiler']" + stage_info['compiler'] + "\n"

    String target_dir
    String src_files
    String log_msg
    int rc = 0

    //////////////////////////////
    // TRACING
    sh "echo 'find .'"
    sh "find ."
    //////////////////////////////

    // NLT Valgrind testing
    target_dir = "unit_test_memcheck_logs"
    src_files = "unit-test-*.memcheck.xml"
    fileOperations([fileCopyOperation(excludes: '',
                                      flattenFiles: false,
                                      includes: src_files,
                                      targetLocation: target_dir)])

    println "TRACE: unitTestPost.groovy:109\n"

    tar_cmd = "tar -czf ${target_dir}.tar.gz ${target_dir}"
    rc = sh(script: tar_cmd, returnStatus: true)
    if (rc != 0) {
      log_msg = String.format("tar command '%s' returned rc=%d\n", tar_cmd, rc)
      println log_msg
    }

    // CaRT Valgrind testing
    target_dir = "valgrind_logs"
    src_files = "Functional on CentOS 8 with Valgrind/cart/*/valgrind.*.memcheck.xml"

    ////////////////////////////////////////////////////////////////////////////////
    echo "TRACE: find \"target_dir\" (line 123)\n"
    sh "find \"" + target_dir  + "\" || :"
    echo "TRACE: find . (line 123)\n"
    sh "find ."
    echo "TRACE: pwd (line 123)\n"
    sh "pwd"
    ////////////////////////////////////////////////////////////////////////////////

    fileOperations([fileCopyOperation(excludes: '',
                                      flattenFiles: true,
                                      renameFiles: true,
                                      includes: src_files,
                                      targetLocation: target_dir)])

    println "TRACE: unitTestPost.groovy:126\n"

    tar_cmd = "tar -czf ${target_dir}.tar.gz ${target_dir}"
    rc = sh(script: tar_cmd, returnStatus: true)
    if (rc != 0) {
      log_msg = String.format("tar command '%s' returned rc=%d\n", tar_cmd, rc)
      println log_msg
    }

  }

  def artifact_list = config.get('artifacts', ['run_test.sh/*'])
  def ignore_failure = config.get('ignore_failure', false)
  artifact_list.each {
    println "TRACE: unitTestPost.groovy:140\n"
    archiveArtifacts artifacts: it,
                     allowEmptyArchive: ignore_failure
  }

  def target_stash = "${stage_info['target']}-${stage_info['compiler']}"
  if (stage_info['build_type']) {
    println "TRACE: unitTestPost.groovy:147\n"
    target_stash += '-' + stage_info['build_type']
  }

  // Coverage instrumented tests and Vagrind are probably mutually exclusive
  if (stage_info['compiler'] == 'covc') {
    println "TRACE: unitTestPost.groovy:153\n"
    return
  }

  if (config['valgrind_stash']) {
    println "TRACE: unitTestPost.groovy:158\n"

    def valgrind_pattern = config.get('valgrind_pattern', '*.memcheck.xml')

    println "TRACE: valgrind_pattern" + valgrind_pattern + "\n"

    stash name: config['valgrind_stash'], includes: valgrind_pattern
  }

  if (stage_info['NLT']) {
    println "TRACE: unitTestPost.groovy:168\n"
    def cb_result = currentBuild.result
    discoverGitReferenceBuild referenceJob: config.get('referenceJobName',
                                              'daos-stack/daos/master'),
                              scm: 'daos-stack/daos'
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
                  name: "Node local testing",
                  tool: issues(pattern: 'vm_test/nlt-errors.json',
                               name: 'NLT results',
                               id: 'VM_test')

    if (cb_result != currentBuild.result) {
      println "TRACE: unitTestPost.groovy:191\n"
      println "The recordIssues step changed result to ${currentBuild.result}."
    }
  }

}
