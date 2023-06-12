// vars/testBranchRE.groovy

  /**
   * testBranchRE step method
   */

def call(String test_branch_type='') {
    // Get a regular expression to match a testing branch name.
    switch (test_branch_type) {
        case 'weekly':
            // Match a weekly test branch, e.g. weekly-testing, weekly-<release>-testing, etc.
            // Also support older weekly-testing-<release> branch names
            return '^weekly-([.-0-9]+)?testing(-2.2)?$'

        case 'provider':
            // Match a provider testing branch, e.g. provider-<provider>-testing,
            // provider-<provider>-<release>-testing, etc.
            // Also support older provider-testing-<provider> and
            // provider-testing-<provider>-<release> branch names
            return '^provider-([.-0-9a-z]+)?testing(-(tcp|ucx))?(-2.2)?$'

        case 'release':
            // Match a release branch name, e.g. release/2.4 (not technically a test branch)
            return '^release/[0-9.]+$'

        default:
            // Match any *-testing branch
            // Also support older branch names not ending in 'testing'
            return '^[-0-9A-Za-z.]+-testing(-(tcp|ucx))?(-2.2)?$'
    }
}
