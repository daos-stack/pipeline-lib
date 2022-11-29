// vars/testBranchRE.groovy

  /**
   * testBranchRE step method
   */

def call() {
    return '^(weekly|provider|soak)-testing(-[a-z]+|)(-[0-9.]+|)'
}