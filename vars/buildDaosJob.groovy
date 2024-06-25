// vars/buildDaosJob.groovy

  /**
   * buildDaosJob step method.
   *
   * The paramater names must match parameters that are the Jenkinsfile for
   * DAOS master.
   *
   *
   */

void call(String branch, String priority) {
    setupDownstreamTesting('daos-stack/daos', branch,
                           (cachedCommitPragma('Test-skip-build', 'false') == 'true' ?
                                               'Skip-build: true' : '') +
                            (cachedCommitPragma('Skip-downstream-test', 'false') == 'true' ?
                                                '\nSkip-test: true' : ''))

    build job: 'daos-stack/daos/' + setupDownstreamTesting.test_branch((branch)),
                parameters: [string(name: 'TestTag',
                                    value: cachedCommitPragma(
                                      'Test-tag',
                                      'load_mpi test_core_files ' +
                                      'test_pool_info_query')),
                             string(name: 'CI_RPM_TEST_VERSION',
                                    value: cachedCommitPragma('Test-skip-build', 'false') == 'true' ?
                                             daosLatestVersion(branch) : ''),
                             string(name: 'BuildPriority', value: priority),
                             booleanParam(name: 'CI_FI_el8_TEST', value: true),
                             booleanParam(name: 'CI_MORE_FUNCTIONAL_PR_TESTS', value: true),
                             booleanParam(name: 'CI_FUNCTIONAL_el9_TEST', value: true),
                             booleanParam(name: 'CI_FUNCTIONAL_el8_TEST', value: true),
                             booleanParam(name: 'CI_FUNCTIONAL_leap15_TEST', value: true),
                             booleanParam(name: 'CI_medium_TEST', value: false),
                             booleanParam(name: 'CI_large_TEST', value: false)
                            ]
}
