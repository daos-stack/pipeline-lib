// vars/scanRpms.groovy

  /**
   * scanRpms step method
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
   * config['daos_pkg_version']  Version of DAOS package.  Required.
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
   * config['inst_rpms']         Additional rpms to install.
   *                             Default 'clamav clamav-devel'
   *
   * config['junit_files']       Junit files to return.
   *                             Default 'maldetect.xml'
   *
   * config['NODELIST']          NODELIST of nodes to run tests on.
   *                             Default env.NODELIST
   *
   * config['target']            Target distribution, such as 'centos7',
   *                             'el8', 'leap15'.  Default based on parsing
   *                             environment variables for the stage.
   *
   * config['test_script']       Script to build RPMs. 
   *                             Default 'ci/rpm_scan_daos_test.sh'.
   *
   */

Map call(Map config = [:]) {

  if (!config['daos_pkg_version']) {
    error 'daos_pkg_version is required.'
  }

  String nodelist = config.get('NODELIST', env.NODELIST)
  String context = config.get('context', 'test/' + env.STAGE_NAME)
  String description = config.get('description', env.STAGE_NAME)
  String test_script = config.get('test_script', fileExists('ci/rpm/scan_daos.sh') ?
                                                   'ci/rpm/scan_daos.sh' :
                                                   'ci/rpm_scan_daos_test.sh')
  String inst_rpms = config.get('inst_rpms', 'clamav clamav-devel')

  Map stage_info = parseStageInfo(config)

  provisionNodes NODELIST: nodelist,
                 node_count: 1,
                 distro: (stage_info['ci_target'] =~ /([a-z]+)(.*)/)[0][1] + stage_info['distro_version'],
                 inst_repos: config.get('inst_repos', ''),
                 inst_rpmms: inst_rpms

  String full_test_script = 'export DAOS_PKG_VERSION=' +
                         config['daos_pkg_version'] + '\n' +
                         test_script

  String junit_files = config.get('junit_files', 'maldetect.xml')
  String failure_artifacts = config.get('failure_artifacts', env.STAGE_NAME)
  boolean ignore_failure = config.get('ignore_failure', false)
  return runTest(script: full_test_script,
                 junit_files: junit_files,
                 failure_artifacts: env.STAGE_NAME,
                 ignore_failure: ignore_failure,
                 description: description,
                 context: context)
}
