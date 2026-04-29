/* groovylint-disable DuplicateStringLiteral */
// vars/buildDaosJob.groovy

  /**
   * buildDaosJob step method.
   *
   * The parameter names must match parameters that are the Jenkinsfile for
   * DAOS master.
   *
   * @param config Map of parameters passed
   */

void call(String branch, String priority) {
    List buildOptions = setupDownstreamTesting('daos-stack/daos', branch,
                           (cachedCommitPragma('Test-skip-build', 'false') == 'true' ?
                                               'Skip-build: true' : '') +
                            (cachedCommitPragma('Skip-downstream-test', 'false') == 'true' ?
                                                '\nSkip-test: true' : ''))
    List buildParameters = [string(name: 'TestTag',
                                    value: cachedCommitPragma(
                                      'Test-tag',
                                      'load_mpi test_core_files ' +
                                      'test_pool_info_query')),
                             string(name: 'CI_RPM_TEST_VERSION',
                                    value: cachedCommitPragma('Test-skip-build', 'false') == 'true' ?
                                             daosLatestVersion(branch) : ''),
                             string(name: 'BuildPriority', value: priority)] + buildOptions
    build job: 'daos-stack/daos/' + setupDownstreamTesting.test_branch((branch)),
                parameters: buildParameters
}
