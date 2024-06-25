// vars/buildDaosJob.groovy

  /**
   * buildDaosJob step method.
   *
   * This is intended only for use in a Jenkinsfile in a Matrix stage.
   * Builds a branch of DAOS based on env.TEST_BRANCH.
   *
   * The paramater names must match parameters that are the Jenkinsfile for
   * DAOS master.
   *
   * @param config Map of parameters passed
   *
   */

Map call(Map config = [:]) {
    build job: 'daos-stack/daos/' + setupDownstreamTesting.test_branch(env.TEST_BRANCH),
                parameters: [string(name: 'TestTag',
                             value: 'load_mpi test_core_files'),
                             // Maybe should only do this if Test-tag: provisioning?
                             string(name: 'CI_RPM_TEST_VERSION',
                                          value: daosLatestVersion(env.TEST_BRANCH)),
                             string(name: 'BuildPriority', value: '2'),
                             booleanParam(name: 'CI_FI_el8_TEST', value: true),
                             booleanParam(name: 'CI_MORE_FUNCTIONAL_PR_TESTS', value: true),
                             booleanParam(name: 'CI_FUNCTIONAL_el9_TEST', value: true),
                             booleanParam(name: 'CI_FUNCTIONAL_el8_TEST', value: true),
                             booleanParam(name: 'CI_FUNCTIONAL_leap15_TEST', value: true),
                             booleanParam(name: 'CI_medium_TEST', value: false),
                             booleanParam(name: 'CI_large_TEST', value: false)
                            ]
}
