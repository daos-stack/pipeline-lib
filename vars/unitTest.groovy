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
   * config['coverage_stash']    Name to stash coverage artifacts
   *                             Name is based on the environment variables
   *                             for the stage if this is coverage test.
   *
   * config['description']       Description to report for SCM status.
   *                             Default env.STAGE_NAME.
   *
   * config['failure_artifacts'] Failure aritfifacts to return.
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
   *                             enviroment variables for the stage.
   *
   * config['stashes']           List of stashes to use.  Default will be
   *                             baed on the environment variables for the
   *                             stage.
   *
   * config['target']            Target distribution, such as 'centos7',
   *                             'leap15'.  Default based on parsing
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
   * config['timeout_time']      Timelimit for test run, not including
   *                             provisioning time.
   *                             Default is 120 Minutes.
   *
   * config['timeout_units']     Timelimit units.  Default is minutes.
   */

def call(Map config = [:]) {

  def nodelist = config.get('NODELIST', env.NODELIST)
  def test_script = config.get('test_script', 'ci/unit/test_main.sh')

  Map stage_info = parseStageInfo(config)

  String inst_rpms = config.get('inst_rpms','')

  if (stage_info['compiler'] == 'covc') {
    if (stage_info['java_pkg']) {
      inst_rpms += " ${stage_info['java_pkg']}"
    }
  }

  provisionNodes NODELIST: nodelist,
                 node_count: stage_info['node_count'],
                 distro: stage_info['target'],
                 inst_repos: config['inst_repos'],
                 inst_rpms: inst_rpms

  def target_stash = "${stage_info['target']}-${stage_info['compiler']}"
  if (stage_info['build_type']) {
    target_stash += '-' + stage_info['build_type']
  }

  def stashes = []
  if (config['stashes']) {
    stashes = config['stashes']
  } else {
    stashes.add("${target_stash}-tests")
    stashes.add("${target_stash}-install")
    stashes.add("${target_stash}-build-vars")
  }

  if (stage_info['compiler'] == 'covc') {

    def tools_url = env.JENKINS_URL +
                    'job/daos-stack/job/tools/job/master' +
                    '/lastSuccessfulBuild/artifact/'
    httpRequest url: tools_url + 'bullseyecoverage-linux.tar',
                httpMode: 'GET',
                outputFile: 'bullseye.tar'
  }

  echo "debug ${stage_info['with_valgrind']}"
  Map params = [:]
  params['stashes'] = stashes
  params['script'] = "SSH_KEY_ARGS=${env.SSH_KEY_ARGS} " +
                     "NODELIST=${nodelist} " +
                     "WITH_VALGRIND=${stage_info['with_valgrind']} " +
                     test_script
  params['junit_files'] = config.get('junit_files', 'test_results/*.xml')
  params['context'] = config.get('context', 'test/' + env.STAGE_NAME)
  params['description'] = config.get('description', env.STAGE_NAME)
  params['ignore_failure'] = config.get('ignore_failure', false)

  def time = config.get('timeout_time', 120) as int
  def unit = config.get('timeout_unit', 'MINUTES')

  timeout(time: time, unit: unit) {
    runTest params
  }
  if (stage_info['compiler'] == 'covc') {
      stash name: config.get('coverage_stash', "${target_stash}-unit-cov"),
            includes: 'test.cov'
  }
}
