/* groovylint-disable VariableName */
// vars/unitTest.groovy

  /**
   * unitTest step method
   *
   * @param config Map of parameters passed
   *
   * config['context']      Context name for SCM to identify the specific
   *                        stage to update status for.
   *                        Default is 'test/' + env.STAGE_NAME.
   *
   *  Important:
   *     The SCM status checking for passing may expect a specific name.
   *
   *     Matrix stages must override this setting to include matrix axes
   *     names to ensure a unique name is generated.
   *
   *     Or the default name has to be changed in a way that is compatible
   *     with a future Matrix implementation.
   *
   * config['code_coverage']     Bullseye code coverage is enabled.
   *
   * config['coverage_stash']    Name to stash coverage artifacts
   *                             Name is based on the environment variables
   *                             for the stage if this is coverage test.
   *
   * config['description']       Description to report for SCM status.
   *                             Default env.STAGE_NAME.
   *
   * config['failure_artifacts'] Failure artifacts to return.
   *                             Default env.STAGE_NAME.
   *
   * config['ignore_failure']    Ignore test failures.  Default false.
   *
   * config['inst_repos']        Additional repositories to use.  Optional.
   *
   * config['inst_rpms']         Additional rpms to install.  Optional
   *
   * config['junit_files']       Junit files to return.
   *                             Default: 'test_results/*.xml'
   *
   * config['NODELIST']          NODELIST of nodes to run tests on.
   *                             Default env.NODELIST
   *
   * config['node_count']        Count of nodes that will actually be used
   *                             the test.  Default will be based on the
   *                             environment variables for the stage.
   *
   * config['stashes']           List of stashes to use.  Default will be
   *                             based on the environment variables for the
   *                             stage.
   *
   * config['target']            Target distribution, such as 'centos7',
   *                             'el8', 'leap15'.  Default based on parsing
   *                             environment variables for the stage.
   *
   * config['test_rpms']         Set to true to test RPMs being built.
   *                             Default env.TEST_RPMS.
   *
   * config['test_script']       Script to run.
   *                             Default is 'ci/unit/test_main.sh' with
   *                             SSH_KEY_ARGS and NODELIST environment
   *                             variables set.
   *
   * config['timeout_time']      Time limit for test run, not including
   *                             provisioning time.
   *                             Default is 120 Minutes.
   *
   * config['timeout_units']     Time limit units.  Default is minutes.
   *
   * config['unstash_opt']       Un-stash -opt-tar instead of -opt,
   *                             default is false.
   *
   * config['unstash_tests']     Un-stash -tests, default is true.
   *
   */

Map afterTest(Map config, Map testRunInfo) {
    Map result = [:]
    result['result'] = testRunInfo['result']
    // Collect log and other result files from the test nodes
    sh label: 'Job Cleanup',
       script: config['always_script']

    // Need to pre-check the Valgrind files here also
    int zero = 0
    String vgrcs
    String memcheck_dir = sanitizedStageName() + '_memcheck_results'
    String valgrind_pattern = config['valgrind_pattern']

    String testResults = config['testResults']
    if (testRunInfo['result_code'] != zero) {
        result['result'] = 'FAILURE'
    } else {
        result['result'] = checkJunitFiles(testResults: testResults)
    }
    if (config['with_valgrind'] || config['NLT']) {
        vgrcs = sh label: 'Check for Valgrind errors',
                   script: "grep -E '<error( |>)' ${valgrind_pattern} || true",
                   returnStdout: true
        if (vgrcs) {
            result['valgrind_check'] = vgrcs
            result['result'] = 'UNSTABLE'
        }
        fileOperations([fileCopyOperation(excludes: '',
                                      flattenFiles: false,
                                      includes: valgrind_pattern,
                                      targetLocation: memcheck_dir)])
        sh label: 'Create tarball of Valgrind xml files',
           script: "tar -cjf ${memcheck_dir}.tar.bz2 ${memcheck_dir}"
    }

    if (config['ignore_failure'] && (result['result'] != 'SUCCESS')) {
        // In this case we have to signal an error in order to change
        // keep stageResult as UNSTABLE, yet change buildResult to 'SUCCESS'
        // We have to do this before junit processes the report as as so that
        // it does not change the buildResult
        catchError(stageResult: 'UNSTABLE',
                   buildResult: 'SUCCESS') {
            error('Marking stage as UNSTABLE to allow pipeline to continue.')
        }
    }

    return result
}

/* groovylint-disable-next-line MethodSize */
Map call(Map config = [:]) {
    long startDate = System.currentTimeMillis()
    String nodelist = config.get('NODELIST', env.NODELIST)
    String test_script = config.get('test_script', 'ci/unit/test_main.sh')
    Map stage_info = parseStageInfo(config)
    String inst_rpms = config.get('inst_rpms', '')
    Boolean code_coverage = config.get('code_coverage', false)

    if (code_coverage) {
        if (stage_info['java_pkg']) {
            inst_rpms += " ${stage_info['java_pkg']}"
        }
    }

    Map runData = provisionNodes(
                 NODELIST: nodelist,
                 node_count: stage_info['node_count'],
                 distro: (stage_info['ci_target'] =~
                          /([a-z]+)(.*)/)[0][1] + stage_info['distro_version'],
                 inst_repos: config.get('inst_repos', ''),
                 inst_rpms: inst_rpms)

    String target_stash = "${stage_info['target']}-${stage_info['compiler']}"
    if (stage_info['build_type']) {
        target_stash += '-' + stage_info['build_type']
    }

    List stashes = []
    if (config['stashes']) {
        stashes = config['stashes']
    } else {
        if (config.get('unstash_tests', true)) {
            stashes.add("${target_stash}-tests")
        }
        stashes.add("${target_stash}-build-vars")
        if (config.get('unstash_opt', false)) {
            stashes.add("${target_stash}-opt-tar")
        } else {
            stashes.add("${target_stash}-install")
        }
    }

    String with_valgrind = stage_info.get('with_valgrind', '')
    Map p = [:]
    p['stashes'] = stashes
    p['script'] = "SSH_KEY_ARGS=${env.SSH_KEY_ARGS} " +
                  "NODELIST=${nodelist} " +
                  "WITH_VALGRIND=${with_valgrind} " +
                  test_script
    p['junit_files'] = config.get('junit_files', 'test_results/*.xml')
    p['context'] = config.get('context', 'test/' + env.STAGE_NAME)
    p['description'] = config.get('description', env.STAGE_NAME)
    // Do not let runTest abort the pipeline as want artifact/log collection.
    p['ignore_failure'] = true
    // runTest no longer knows now to notify for Unit Tests
    p['notify_result'] = false
    int time = config.get('timeout_time', 120) as int
    String unit = config.get('timeout_unit', 'MINUTES')

    Map runTestData = [:]
    timeout(time: time, unit: unit) {
        runTestData = runTest p
        runTestData.each { resultKey, data -> runData[resultKey] = data }
    }
    p['always_script'] = stage_info.get('always_script',
                                        'ci/unit/test_post_always.sh')
    p['valgrind_pattern'] = stage_info.get('valgrind_pattern',
                                           'unit-test-*memcheck.xml')
    p['testResults'] = stage_info.get('testResults', 'test_results/*.xml')
    p['with_valgrind'] = with_valgrind
    p['NLT'] = stage_info['NLT']
    runTestData = afterTest(p, runData)
    runTestData.each { resultKey, data -> runData[resultKey] = data }

    if (code_coverage) {
        stash name: config.get('coverage_stash', "${target_stash}-unit-cov"),
              includes: '**/test.cov'
              allowEmpty: true
    }
    int runTime = durationSeconds(startDate)
    runData['unittest_time'] = runTime

    // Update the stash after checking junit/valgrind
    String results_map = 'results_map_' + sanitizedStageName()
    // Use the original ignore_failure setting for post section
    runData['ignore_failure'] = config.get('ignore_failure', false)
    writeYaml file: results_map,
              data: runData,
              overwrite: true
    stash name: results_map,
          includes: results_map

    // Stash any optional test coverage reports for the stage
    String code_coverage_name = 'code_coverage_' + sanitizedStageName()
    stash name: code_coverage_name,
          includes: '**/code_coverage.json',
          allowEmpty: true

    return runData
}
