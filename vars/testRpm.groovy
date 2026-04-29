// vars/testRpm.groovy

  /**
   * testRpm step method
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
   * config['failure_artifacts'] Failure artifacts to return.
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
   * config['profile']           Profile to use.  Default 'daos_ci'.
   *
   * config['target']            Target distribution, such as 'centos7',
   *                             'el8', 'leap15'.  Default based on parsing
   *                             environment variables for the stage.
   *
   * config['test_script']       Script to build RPMs.
   *                             Default 'ci/rpm/test_daos.sh'.
   */

Map call(Map config = [:]) {
    Map runData = provisionNodes NODELIST: config.get('NODELIST', env.NODELIST),
                                 node_count: 1,
                                 profile: config.get('profile', 'daos_ci'),
                                 distro: parseStageInfo(config)['ci_target'],
                                 inst_repos: config.get('inst_repos', ''),
                                 inst_rpms: config.get('inst_rpms', '')

    Map runtestData = runTest script: 'export DAOS_PKG_VERSION="' +
                                      config['daos_pkg_version'] + '"\n' +
                                      config.get('test_script', 'ci/rpm/test_daos.sh'),
                    junit_files: config.get('junit_files', null),
                    failure_artifacts: config.get('failure_artifacts', env.STAGE_NAME),
                    ignore_failure: config.get('ignore_failure', false),
                    description: config.get('description', env.STAGE_NAME),
                    context: config.get('context', 'test/' + env.STAGE_NAME)
    runtestData.each { resultKey, data -> runData[resultKey] = data }
    long endDate = System.currentTimeMillis()
    runData['rpmtest_time'] = durationSeconds(endDate)

    return runData
}
