// vars/testBranchRE.groovy

  /**
   * testBranchRE step method
   */

def call() {
    // Match any *-testing branch
    // Also support older branch names not ending in '-testing'
    return '^[.-\w]+-testing(-(tcp|ucx))?(-2.2)?$'
}
