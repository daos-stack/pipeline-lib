/* groovylint-disable DuplicateNumberLiteral, DuplicateStringLiteral, VariableName */
// vars/valgrindReportPublish.groovy

  /**
   * Valgrind Report Publish step method
   *
   * Consolidate Valgrind data for publishing.
   *
   * @param config Map of parameters passed
   *
   * config['ignore_failure']      Ignore test failures.  Default false.
   *
   * config['valgrind_pattern']    Pattern for Valgrind files.
   *                               Default: '*.memcheck.xml'
   *
   * config['valgrind_stashes']    list of stashes for valgrind results.
   */

void call(Map config = [:]) {
    List stashes = []
    if (config['valgrind_stashes']) {
        stashes = config['valgrind_stashes']
  } else {
        // Older code publishes the valgrind in the same stage as ran the
        // valgrind test.
        // That does not work if you have multiple valgrind stages running.
        // Need to have only one Valgrind publish stage
        println 'No valgrind_stashes passed!   Running older code!'
    }

    fileOperations([fileDeleteOperation(includes: '*.memcheck.xml')])

    int stash_cnt = 0
    stashes.each { stash ->
        try {
            unstash stash
            println("Success un-stashing ${stash}.")
            stash_cnt++
    } catch (hudson.AbortException ex) {
            println("Ignoring failure to unstash ${stash}.  Perhaps the stage was skipped?")
        }
    }

    if (stash_cnt < 1) {
        println('No valgrind XML files found, skipping valgrind publishing')
        return
    }

    Boolean ignore_failure = config.get('ignore_failure', false)

    String valgrind_pattern = config.get('valgrind_pattern', '*.memcheck.xml')

    if (findFiles(glob: valgrind_pattern).length == 0) {
        println('No Valgrind files found')
        return
    }

    String cb_result = currentBuild.result
    recordIssues enabledForFailure: true,
                 failOnError: !ignore_failure,
                 ignoreQualityGate: false,
                 qualityGates: [
                   [threshold: 1, type: 'TOTAL_ERROR'],
                   [threshold: 1, type: 'TOTAL_HIGH']],
                 name: 'Valgrind Memory Check',
                 tool: valgrind(pattern: valgrind_pattern,
                               name: 'Valgrind Results',
                               id: 'valgrind')

    if (cb_result != currentBuild.result) {
        println "The recordIssues step changed result to ${currentBuild.result}."
    }
}
