// vars/emailext.groovy

/**
 * run.groovy
 *
 * Wrapper for emailext step to allow quiet operation for a staging
 * system.
 *
 */

def call(Map config = [:]) {
    println "Entering emailext override."

    if (env.DAOS_JENKINS_NOTIFY_STATUS == null) {
        println "Jenkins not configured to notify users of failed builds."
        return
    }

    steps.emailext(config)
}
