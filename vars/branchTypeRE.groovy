/* groovylint-disable ParameterName */
// vars/branchTypeRE.groovy

  /**
   * branchTypeRE step method
   */

String call(String branch_type) {
    // Get a regular expression to match a branch name of the specified type.
    switch (branch_type) {
        case 'weekly':
            // Match a weekly test branch, e.g. weekly-testing, weekly-<release>-testing, etc.
            // Also support older weekly-testing-<release> branch names
            println('Trying to match /^weekly-(?:[-\\d.]+)?testing(?:-2\\.2)?$/to for weekly')
            return(/^weekly-(?:[-\d.]+)?testing(?:-2\.2)?$/)

        case 'provider':
            // Match a provider testing branch, e.g. provider-<provider>-testing,
            // provider-<provider>-<release>-testing, etc.
            // Also support older provider-testing-<provider> and
            // provider-testing-<provider>-<release> branch names
            println('Trying to match /^provider-(?:[-\\da-z.]+)?testing(?:-(?:tcp|ucx))?(?:-2\\.2)?$/ for provider')
            return(/^provider-(?:[-\da-z.]+)?testing(?:-(?:tcp|ucx))?(?:-2\.2)?$/)

        case 'release':
            // Match a release branch name, e.g. release/2.4
            println('Trying to match /^release\\/[\\d.]+$/ for release')
            return(/^release\/[\d.]+$/)

        case 'downstream':
            // Match a downstream testing branch e.g. ci-daos-do-packer-ofed-images-PR-11-release-2.2,
            //                                        ci-daos-do-packer-ofed-images-PR-11-master
            //                                        ci-daos-stack-pipeline-lib-PR-415-weekly-testing
            //                                        ci-daos-stack-pipeline-lib-PR-415-weekly-2.4-testing
            /* groovylint-disable-next-line LineLength */
            println('Trying to match /^ci-daos-.+-(?:release\\/[\\d.]+|master|weekly-(?:[\\d.]+-)?testing)$/ for downstream')
            return(/^ci-daos-.+-(?:release\/[\d.]+|master|weekly-(?:[\d.]+-)?testing)$/)

        case 'testing':
            // Match any *-testing branch (including weekly and provider)
            // Also support older branch names not ending in 'testing'
            println('Trying to match ' + testBranchRE() + ' for testing')
            return(testBranchRE())

        default:
            error "Unsupported branch type: ${branch_type}"
    }
}
