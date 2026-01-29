// vars/emailext.groovy

/**
 * emailext.groovy
 *
 * Wrapper for emailext step to allow quiet operation for a staging
 * system.
 */

void call(Map config = [:]) {
    if (env.DAOS_JENKINS_NOTIFY_STATUS == null) {
        println 'Jenkins not configured to notify users of failed builds.'
        return
    }

    steps.emailext(config)
}
