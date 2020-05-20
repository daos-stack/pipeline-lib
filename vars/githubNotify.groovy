// vars/githubNotifiy.groovy

/**
 * githubNotify.groovy
 *
 * Wrapper for githubNotify step to allow quiet operation for a staging
 * system.
 *
 */

def call(Map config = [:]) {
    println "Redirecting to scmNotify."

    scmNotify(config)
}
