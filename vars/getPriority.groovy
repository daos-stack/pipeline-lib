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
    println("Setting build priroity to: " + p)

    if (!fileExists('ci/jira_query.py')) {
        return p
    }

     p = sh(label: "Determine Job priority",
            script: ["GITHUB_BASE_REF=${target_branch}",
		     "PR_TITLE=" + env.CHANGE_TITLE,
                     "ci/jira_query.py", "--priority-only"].join(' '),
            returnStatus: true).trim()
 
    println("Setting build priroity to: " + p)
    return p
}
