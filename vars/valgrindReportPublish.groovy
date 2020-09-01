// vars/valgrindReportPublish.groovy

  /**
   * Valgrind Report Publish step method
   *
   * Consolidiate Valgrind data for publising.
   *
   * @param config Map of parameters passed
   *
   *
   * config['ignore_failure']      Ignore test failures.  Default false.
   *
   * config['valgrind_pattern']    Pattern for Valgind files.
   *                               Default: 'dnt.*.memcheck.xml'
   *
   * config['valgrind_stashes']    list of stashes for valgrind results.
   *
   *
   */

def call(Map config = [:]) {

  def stashes = []
  if (config['valgrind_stashes']) {
    stashes = config['valgrind_stashes']
  } else {
    // Older code publishes the valgrind in the same stage as ran the
    // vagrind test.
    // That does not work if you have multiple valgrind stages running.
    // Need to have only one Valgrind publish stage
    println "No valgrind_stashes passed!   Running older code!"
  }

  int stash_cnt=0
  stashes.each {
    unstash it
  }

  def ignore_failure = config.get('ignore_failure', false)

  def cb_result = currentBuild.result
  publishValgrind failBuildOnInvalidReports: true,
                  failBuildOnMissingReports: !ignore_failure,
                  failThresholdDefinitelyLost: '0',
                  failThresholdInvalidReadWrite: '0',
                  failThresholdTotal: '0',
                  pattern: config.get('valgrind_pattern', '*.memcheck.xml'),
                  publishResultsForAbortedBuilds: false,
                  publishResultsForFailedBuilds: true,
                  sourceSubstitutionPaths: '',
                  unstableThresholdDefinitelyLost: '0',
                  unstableThresholdInvalidReadWrite: '0',
                  unstableThresholdTotal: '0'

  if (cb_result != currentBuild.result) {
    println "The publishValgrind step changed result to ${currentBuild.result}."
  }
}
