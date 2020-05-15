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

  if (env.DAOS_JENKINS_NOTIFY_STATUS == null) {
    println "Jenkins not configured to notify SCM repository of builds."
    return
  }
  try {
    scmNotifySystem(config)
  } catch (java.lang.NoSuchMethodError e) {
    println 'Could not find a scmNotifySystem step in a shared groovy library.'
  }
}
