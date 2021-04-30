// vars/storagePrepTest.groovy

  /**
   * storagePrepTest step method
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
   *                             'leap15'.  Default based on parsing
   *                             environment variables for the stage.
   *
   * config['test_rpms']         Set to true to test RPMs being built.
   *                             Default env.TEST_RPMS.
   *
   * config['test_tag']          Avocado tag to test. (Not currently used)
   *                             Default determined by parseStageInfo().
   *
   */

def call(Map config = [:]) {

  println("Entering Storage Prep Test")
  def nodelist = config.get('NODELIST', env.NODELIST)
  def context = config.get('context', 'test/' + env.STAGE_NAME)
  def description = config.get('description', env.STAGE_NAME)
 
  println("Calling parseStage_info")
  Map stage_info = parseStageInfo(config)

  println("config = -${config}-")

  println("Calling ProvisionNodes")
  provisionNodes NODELIST: nodelist,
                 node_count: stage_info['node_count'],
                 distro: stage_info['ci_target'],
                 inst_repos: config.get('inst_repos', ''),
                 inst_rpms: config.get('inst_rpms', '')

  def stashes = []
  if (config['stashes']) {
    stashes = config['stashes']
  } else {
    def target_compiler = "${stage_info['target']}-${stage_info['compiler']}"
    stashes.add("${target_compiler}-install")
    stashes.add("${target_compiler}-build-vars")
  }

  Map params = [:]
  params['stashes'] = stashes
  params['test_rpms'] = config.get('test_rpms', env.TEST_RPMS)
  params['pragma_suffix'] = stage_info['pragma_suffix']
  params['test_tag'] =  config.get('test_tag', stage_info['test_tag'])
  params['node_count'] = stage_info['node_count']
  params['ftest_arg'] = stage_info['ftest_arg']
  params['context'] = context
  params['description'] = description

  if (!fileExists('ci/storage/test_main.sh')) {
    println("No storage Prep script found!")
    return
  }

  Boolean test_rpms = false
  if (config['test_rpms'] == "true") {
    test_rpms = true
  }

  config['script'] = 'export NODE_COUNT="' + stage_info['node_count'] + '"\n ' +
                     'export OPERATIONS_EMAIL="' +
                         env.OPERATIONS_EMAIL + '"\n ' +
                     'export DAOS_PKG_VERSION=' +
                         daosPackagesVersion("1000") + '\n' +
                     'ci/storage/test_main.sh'
                                     
  if (!config['failure_artifacts']) {
    config['failure_artifacts'] = env.STAGE_NAME
  }

  if (test_rpms && config['stashes']) {
    // we don't need (and might not even have) stashes if testing
    // from RPMs
    config.remove('stashes')
  }

  config.remove('pragma_suffix')
  config.remove('test_tag')
  config.remove('ftest_arg')
  config.remove('node_count')
  config.remove('test_rpms')

  runTest(config)
}
