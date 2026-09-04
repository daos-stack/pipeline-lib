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

/**
 * Build the list of commit pragmas to apply to the downstream test branch.
 *
 * Downstream testing exists to prove that this library loads and drives the
 * downstream pipeline, which is settled by the build stages.  Running the
 * functional and hardware test stages re-tests DAOS rather than the library,
 * so a build-only run is the default and a full run is opt-in via
 * 'Full-downstream-test: true'.
 *
 * @return List of pragma lines
 */
List downstreamPragmas() {
    List pragmas = []
    if (cachedCommitPragma('Test-skip-build', 'false') == 'true') {
        pragmas.add('Skip-build: true')
    }
    // 'Skip-downstream-test' predates the build-only default and is now
    // implied by it, but is still honoured so that it overrides an
    // explicit request for a full run.
    Boolean fullTest = cachedCommitPragma('Full-downstream-test', 'false') == 'true' &&
                       cachedCommitPragma('Skip-downstream-test', 'false') != 'true'
    if (!fullTest) {
        pragmas.add('Skip-test: true')
        // 'Skip-test' only covers the 'Test' parent stage.  Hardware stages on
        // daos master and release/2.8 are gated by their own parent pragma, and
        // the fault injection stage on release/2.6 is gated by skipStage() which
        // does not consult 'skip-test', so all three are required for a
        // genuinely build-only run.
        pragmas.add('Skip-test-hardware: true')
        pragmas.add('Skip-fault-injection-test: true')
    }
    return pragmas
}

void call(String branch, String priority) {
    List buildOptions = setupDownstreamTesting('daos-stack/daos', branch,
                                               downstreamPragmas().join('\n'))
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
