// vars/getPriority.groovy

  /**
   * getPriority step method
   *
   * @param None
   *
   */

def call() {
      if (env.BRANCH_NAME == 'weekly-testing') {
        string p = '2'
    } else {
        string p = ''
    }
    return p
}
