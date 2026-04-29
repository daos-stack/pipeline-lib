/* groovylint-disable UnusedMethodParameter */
// vars/startedByTimer.groovy

/**
 * Return True the build was started by a timer
 */
Boolean call(Map config = [:]) {
    /* groovylint-disable-next-line UnnecessaryGetter */
    return currentBuild.getBuildCauses().toString().contains('hudson.triggers.TimerTrigger$TimerTriggerCause')
}
