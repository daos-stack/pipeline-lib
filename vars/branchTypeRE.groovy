// vars/branchTypeRE.groovy

  /**
   * branchTypeRE step method
   */

def call(String branch_type) {
    // Get a regular expression to match a branch name of the specified type.
    switch (branch_type) {
        case 'weekly':
            // Match a weekly test branch, e.g. weekly-testing, weekly-<release>-testing, etc.
            // Also support older weekly-testing-<release> branch names
            return '^weekly-([.-\d]+)?testing(-2.2)?$'

        case 'provider':
            // Match a provider testing branch, e.g. provider-<provider>-testing,
            // provider-<provider>-<release>-testing, etc.
            // Also support older provider-testing-<provider> and
            // provider-testing-<provider>-<release> branch names
            return '^provider-([.-\w]+)?testing(-(tcp|ucx))?(-2.2)?$'

        case 'release':
            // Match a release branch name, e.g. release/2.4
            return '^release/[0-9.]+$'

        case 'testing':
            // Match any *-testing branch (including weekly and provider)
            // Also support older branch names not ending in 'testing'
            return testBranchRE()

        default:
            error "Unsupported branch type: ${branch_type}"
    }
}
