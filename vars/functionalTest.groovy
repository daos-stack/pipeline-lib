/* groovylint-disable VariableName */
// vars/functionalTest.groovy
/*
 * Copyright 2020-2024 Intel Corporation
 * Copyright 2025 Hewlett Packard Enterprise Development LP
 */

  /**
   * functionalTest step method
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
   * config['description']       Description to report for SCM status.
   *                             Default env.STAGE_NAME.
   *
   * config['failure_artifacts'] Failure aritfifacts to return.
   *                             Default env.STAGE_NAME.
   *
   * config['ignore_failure']    Ignore test failures.  Default false.
   * config['inst_repos']        Additional repositories to use.  Optional.
   *
   * config['inst_rpms']         Additional rpms to install.  Optional
   *
   * config['junit_files']       Junit files to return.  Optional.
   *
   * config['NODELIST']          NODELIST of nodes to run tests on.
   *                             Default env.NODELIST
   *
   * config['node_count']        Count of nodes that will actually be used
   *                             the test.  Default will be based on the
   *                             enviroment variables for the stage.
   *
   * config['stashes']           List of stashes to use.  Default will be
   *                             baed on the environment variables for the
   *                             stage.
   *
   * config['target']            Target distribution, such as 'centos7',
   *                             'el8', 'leap15'.  Default based on parsing
   *                             environment variables for the stage.
   *
   * config['test_rpms']         Set to true to test RPMs being built.
   *                             Default env.TEST_RPMS.
   *
   * config['test_tag']          Avocado tag to test.
   *                             Default determined by parseStageInfo().
   *
   * config['ftest_arg']         Functional test launch.py arguments.
   *                             Default determined by parseStageInfo().
   *
   * config['details_stash']    Stash name for functional test details.
   */

Map call(Map config = [:]) {
    Date startDate = new Date()
    String nodelist = config.get('NODELIST', env.NODELIST)
    String context = config.get('context', 'test/' + env.STAGE_NAME)
    String description = config.get('description', env.STAGE_NAME)

    Map stage_info = parseStageInfo(config)

    String image_version = config.get('image_version') ?:
        (stage_info['ci_target'] =~ /([a-z]+)(.*)/)[0][1] + stage_info['distro_version']

    // Install any additional rpms required for this stage
    String stage_inst_rpms = config.get('inst_rpms', '')
    if (stage_info['stage_rpms']) {
        stage_inst_rpms = stage_info['stage_rpms'] + ' ' + stage_inst_rpms
    }

    // Check for a mis-configured cluster
    String[] nodes = nodelist.split(',')
    if (nodes.size() < stage_info['node_count']) {
        String message = "CI Cluster needs ${stage_info['node_count']} only has ${nodes.size()}"
        buildAgentControl(action: 'offline',
                          message: message,
                          subject: 'CI Test failure - CI Configuration test issue.')
    }

    echo "Running provisionNodes() on ${nodelist} with the ${image_version} image"
    Map runData = provisionNodes(
                 NODELIST: nodelist,
                 node_count: stage_info['node_count'],
                 distro: image_version,
                 inst_repos: config.get('inst_repos', ''),
                 inst_rpms: stage_inst_rpms)

    List stashes = []
    if (config['stashes']) {
        stashes = config['stashes']
    } else {
        String target_compiler = "${stage_info['target']}-${stage_info['compiler']}"
        stashes.add("${target_compiler}-install")
        stashes.add("${target_compiler}-build-vars")
    }

    Map run_test_config = [:]
    run_test_config['stashes'] = stashes
    run_test_config['test_rpms'] = config.get('test_rpms', env.TEST_RPMS)
    run_test_config['pragma_suffix'] = stage_info['pragma_suffix']
    run_test_config['test_tag'] =  config.get('test_tag', stage_info['test_tag'])
    run_test_config['node_count'] = stage_info['node_count']
    run_test_config['ftest_arg'] = config.get('ftest_arg', stage_info['ftest_arg'])
    run_test_config['context'] = context
    run_test_config['description'] = description
    run_test_config['details_stash'] = config.get(
        'details_stash', 'func' + stage_info['pragma_suffix'] + '-details')

    String script = 'if ! pip3 install'
    script += ''' --upgrade --upgrade-strategy only-if-needed launchable; then
                    set +e
                    echo "Failed to install launchable"
                    id
                    pip3 list --user || true
                    find ~/.local/lib -type d
                    hostname
                    pip3 --version
                    pip3 index versions launchable
                    pip3 install --user launchable==
                    exit 1
                fi
                pip3 list --user || true
                '''
    sh label: 'Install Launchable',
       script: script

    try {
        withCredentials([string(credentialsId: 'launchable-test', variable: 'LAUNCHABLE_TOKEN')]) {
            sh label: 'Send build data',
            /* groovylint-disable-next-line GStringExpressionWithinString */
            script: '''export PATH=$PATH:$HOME/.local/bin
                    launchable record build --name ${BUILD_TAG//%2F/-} --source src=.
                    launchable subset --time 60m --build ${BUILD_TAG//%2F/-} ''' +
                    '--get-tests-from-previous-sessions --rest=rest.txt raw > subset.txt'
        }
    /* groovylint-disable-next-line CatchException */
    } catch (Exception error) {
        println(
            "Ignoring failure to record " + env.STAGE_NAME + " tests with launchable: " +
            error.getMessage())
    }

    Map runtestData = [:]
    if (config.get('test_function', 'runTestFunctional') ==
      'runTestFunctionalV2') {
        runtestData = runTestFunctionalV2 run_test_config
    } else {
        runtestData = runTestFunctional run_test_config
    }
    runtestData.each { resultKey, data -> runData[resultKey] = data }

    int runTime = durationSeconds(startDate)
    runData['funtionaltest_time'] = runTime
    return runData
}
