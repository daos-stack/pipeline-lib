// vars/daosStackNotifiyStatus.groovy

/**
 * run.groovy
 *
 * Wrapper for daosStackNotifyStatusSystem.
 * 
 * The daosStacknotifyStatusSystem must be provided as a shared
 * groovy library local to the running Jenkins for this routine.
 *
 * If it is not provided this routine will not do anything.
 *
 * This is to allow re-using the Jenkinsfiles and pipeline-lib files for
 * other jenkins environments.
 *
 * @param config Map of parameters passed
 * @return none
 *
 * The config[] parameters are the githubNotify step parameters.
 * If you are using a different type of status notification, your
 * notifyStatusSystem routine will need to translate them.
 */

def call(Map config = [:]) {

    try {
        return daosStackNotifyStatusSystem(config)
    } catch (java.lang.NoSuchMethodError e) {
        println('Could not find a daosStackNotifySystem step in' +
                ' a shared groovy library')
        return
    }
}
