/* groovylint-disable CouldBeElvis, DuplicateStringLiteral */
// vars/scmNotify.groovy

 /* This provides a way of notifying the SCM such as GitLab/GitHub with
  * the status of the build stage in progress.
  *
  * The scmNotifySystem must be provided as a shared
  * groovy library local to the running Jenkins for this routine.
  *
  * If it is not provided this routine will not do anything.
  */

  /**
   * scmNotify step method
   *
   * @param config Map of parameters passed
   *
   * See the githubNotify pipeline step for the parameters to pass.
   */
void call(Map config = [:]) {
    String errorText = 'Jenkins not configured to notify SCM repository of builds.'
    if (env.DAOS_JENKINS_NOTIFY_STATUS == null) {
        println errorText
        return
    }
    try {
        if (!config['credentialsId']) {
            config['credentialsId'] = scmStatusIdSystem()
        }
    } catch (java.lang.NoSuchMethodError e) {
        // Did not find a shared scmStatusIdSystem routine.
        // Assume DAOS_JENKINS_NOTIFY_STATUS contains a credential id.
        config['credentialsId'] = env.DAOS_JENKINS_NOTIFY_STATUS
    }

    int notifyAttempt = 0

    try {
        retry(3) {
            notifyAttempt++

            try {
                scmNotifyTrusted(config)
            } catch (Exception e) {
                echo "WARNING: GitHub notification attempt ${notifyAttempt}/3 failed " +
                    "(${e.message})."

                if (notifyAttempt < 3) {
                    sleep(time: 5, unit: 'SECONDS')
                }

                // Required for the Jenkins retry step to run the next attempt.
                throw e
            }
        }
    } catch (Exception e) {
        echo "ERROR: could not notify GitHub after ${notifyAttempt} attempts " +
            "(${e.message}); continuing because status notification is non-fatal."
    }
}
