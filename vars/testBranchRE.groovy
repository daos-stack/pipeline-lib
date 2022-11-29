// vars/testBranchRE.groovy

  /**
   * testBranchRE step method
   */

def call() {
    return '^[-0-9A-Za-z.]+-testing(-(tcp|ucx))?(-2.2)?$'
}