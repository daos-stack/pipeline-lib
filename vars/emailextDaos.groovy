/* groovylint-disable DuplicateStringLiteral */
// vars/emailextDaos.groovy

/**
 * emailextDaos.groovy
 *
 * email responsible parties.
 *
 * in addition to https://jenkins.io/doc/pipeline/steps/email-ext/:
 * config['onPR']       Send e-mail when called from a PR.  Default false
 */

void call(Map config = [:]) {
    if (env.DAOS_JENKINS_NOTIFY_STATUS == null) {
        println 'Jenkins not configured to notify users of failed builds.'
        return
    }

    boolean onPR = false
    if (config['onPR'] && config['onPR'] == true) {
        onPR = true
    }

    // Don't send e-mails on PRs
    if (env.CHANGE_ID && !onPR) {
        return
    }

    config.remove('onPR')

    steps.emailext(config)
}
