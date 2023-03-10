// vars/startedByUpstream.groovy

/**
 * Return True the build was started by an upstream job
 */
/* groovylint-disable-next-line UnusedMethodParameter */
boolean call(Map config = [:]) {
    // groovylint-disable-next-line UnnecessaryGetter
    return currentBuild.getBuildCauses().toString().contains(
      'org.jenkinsci.plugins.workflow.support.steps.build.BuildUpstreamCause')
}
