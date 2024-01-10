// vars/getPriority.groovy

  /**
   * getPriority step method
   *
   * @param None
   *
   */

String call() {
    String p = ''
    if (env.BRANCH_NAME =~ testBranchRE()) {
        p = '2'
    } else {
        node(label: 'lightweight') {
            p = cachedCommitPragma('Priority')
        }
    }

    println("Setting build priority to: " + p)
    return p
}
