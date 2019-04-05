// vars/githubNotifiy.groovy

/**
 * run.groovy
 *
 * Wrapper for githubNotify step to allow quiet operation for a staging
 * system.
 *
 */

def call(Map config = [:]) {
    println "Entering githubNotify override."

    if (env.DAOS_JENKINS_NOTIFY_STATUS == null) {
        println "Jenkins not configured to notify github of builds."
        return
    }

    steps.githubNotify(config)
}
