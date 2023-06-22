// vars/getPriority.groovy

  /**
   * getPriority step method
   *
   * @param None
   *
   */

def call() {
    if (env.BRANCH_NAME =~ testBranchRE()) {
        string p = '2'
    } else {
        string p = ''
    }
    println("Setting build priroity to: " + p)
    return p
}
