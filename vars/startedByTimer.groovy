/* groovylint-disable VariableName */
// vars/startedByTimer.groovy

/**
 * Return True the build was started by a timer
 */
Boolean call() {

  Boolean simulate_timed_job = cachedCommitPragma('Simulate-timed-job') == 'true'

  if (simulate_timed_job) {
      println('Simulating timed job behaviour')
  }

  return simulate_timed_job ||
         /* groovylint-disable-next-line UnnecessaryGetter */
         currentBuild.getBuildCauses().toString().contains('hudson.triggers.TimerTrigger$TimerTriggerCause')

}
