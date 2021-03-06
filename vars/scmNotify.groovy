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

def call(Map config = [:]) {

  def errtxt = 'Jenkins not configured to notify SCM repository of builds.'
  if (env.DAOS_JENKINS_NOTIFY_STATUS == null) {
    println errtxt
    return
  }
  try {
    if (! config['credentialsId']) {
      config['credentialsId'] = scmStatusIdSystem()
    }
  } catch (java.lang.NoSuchMethodError e) {
    // Did not find a shared scmStatusIdSystem routine.
    // Assume DAOS_JENKINS_NOTIFY_STATUS contains a credential id.
    config['credentialsId'] = env.DAOS_JENKINS_NOTIFY_STATUS
  }
  scmNotifyTrusted(config)
}
