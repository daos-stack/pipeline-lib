// vars/getPriority.groovy

  /**
   * getPriority step method
   *
   * @param None
   *
   */

def call() {
      if (startedByTimer() ||
        env.BRANCH_NAME.startsWith("release/") ||
        env.BRANCH_NAME == 'weekly-testing') {
        string p = '2'
    } else {
        string p = ''
    }
    return p
}
