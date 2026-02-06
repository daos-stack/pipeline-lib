// vars/startedByUser.groovy

/**
 * Return True if the build was caused by a User (I.e. Build Now)
 */
/* groovylint-disable-next-line UnusedMethodParameter */
Boolean call(Map config = [:]) {
    /* groovylint-disable-next-line UnnecessaryGetter */
    return currentBuild.getBuildCauses().toString().contains('hudson.model.Cause$UserIdCause')
}
