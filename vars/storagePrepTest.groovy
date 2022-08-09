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
   *                             'el8', 'leap15'.  Default based on parsing
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

  String nodelist = config.get('NODELIST', env.NODELIST)
  String context = config.get('context', 'test/' + env.STAGE_NAME)
  String description = config.get('description', env.STAGE_NAME)
 
  Map stage_info = parseStageInfo(config)

  provisionNodes NODELIST: nodelist,
                 node_count: stage_info['node_count'],
                 distro: (stage_info['ci_target'] =~ /([a-z]+)(.*)/)[0][1] + stage_info['distro_version'],
                 inst_repos: config.get('inst_repos', ''),
                 inst_rpms: config.get('inst_rpms', '')

    Map p = [:]
    p['context'] = context
    p['description'] = description

  if (!fileExists('ci/storage/test_main.sh')) {
    println("No storage Prep script found!")
    return
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

  runTest(config)
}
