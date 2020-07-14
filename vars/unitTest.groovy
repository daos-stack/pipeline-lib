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
   *
   * config['valgrind']          Run unit tests with Valgrind: 'memcheck'.
   *                             Optional.
   */

def call(Map config = [:]) {

  def nodelist = config.get('NODELIST', env.NODELIST)
  def test_script = config.get('test_script', 'ci/unit/test_main.sh')

  Map stage_info = parseStageInfo(config)

  provisionNodes NODELIST: nodelist,
                 node_count: stage_info['node_count'],
                 distro: stage_info['target'],
                 inst_repos: config['inst_repos'],
                 inst_rpms: config['inst_rpms']

  def stashes = []
  if (config['stashes']) {
    stashes = config['stashes']
  } else {
    def target_compiler = "${stage_info['target']}-${stage_info['compiler']}"
    stashes.add("${target_compiler}-tests")
    stashes.add("${target_compiler}-install")
    stashes.add("${target_compiler}-build-vars")
  }
  
  def valgrind = config.get('valgrind', '')

  Map params = [:]
  params['stashes'] = stashes
  params['script'] = "SSH_KEY_ARGS=${env.SSH_KEY_ARGS} " +
                     "NODELIST=${nodelist} " +
                     test_script + " " + valgrind
  params['junit_files'] = config.get('junit_files', 'test_results/*.xml')
  params['context'] = config.get('context', 'test/' + env.STAGE_NAME)
  params['description'] = config.get('description', env.STAGE_NAME)

  def time = config.get('timeout_time', 120) as int
  def unit = config.get('timeout_unit', 'MINUTES')

  timeout(time: time, unit: unit) {
    runTest params
  }
}
