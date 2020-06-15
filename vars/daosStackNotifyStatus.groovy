// vars/daosStackNotifiyStatus.groovy

/**
 * run.groovy
 *
 * Wrapper for scmNotify.
 *
 * This routine is deprecated and will be removed when it is verified
 * that nothing is calling it.
 */

def call(Map config = [:]) {
  scmNotify(config)
}
