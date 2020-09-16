// vars/fnCovTest.groovy

  /**
   * fnCovTest step method
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
   * config['compiler']          Compiler used 'gcc', 'icc', clang, and 'covc'.
   *                             Default is 'gcc
   */

def call(Map config = [:]) {

  def nodelist = config.get('NODELIST', env.NODELIST)
  def context = config.get('context', 'test/' + env.STAGE_NAME)
  def description = config.get('description', env.STAGE_NAME)
 
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
                 inst_rpms: config['inst_rpms']

  def stashes = []
  if (config['stashes']) {
    stashes = config['stashes']
  } else {
    def target_compiler = "${stage_info['target']}-${stage_info['compiler']}"
    stashes.add("${target_compiler}-install")
    stashes.add("${target_compiler}-build-vars")
  }

  if (stage_info['compiler'] == 'covc') {

    def tools_url = env.JENKINS_URL +
                    'job/daos-stack/job/tools/job/master' +
                    '/lastSuccessfulBuild/artifact/'
    httpRequest url: tools_url + 'bullseyecoverage-linux.tar',
                httpMode: 'GET',
                outputFile: 'bullseye.tar'
  }

  Map params = [:]
  params['stashes'] = stashes
  params['test_rpms'] = config.get('test_rpms', env.TEST_RPMS)
  params['pragma_suffix'] = stage_info['pragma_suffix']
  params['test_tag'] = stage_info['test_tag']
  params['node_count'] = stage_info['node_count']
  params['ftest_arg'] = stage_info['ftest_arg']
  params['compiler'] = stage_info['compiler']
  params['context'] = context
  params['description'] = description

  runTestFnCov params

}
