/* groovylint-disable DuplicateNumberLiteral */
// vars/runScriptWithStashes.groovy

/**
 * // vars/runScriptWithStashes.groovy
 *
 * Run a shell script with access to specified stashes.
 *
 * @param kwargs Map containing the following optional arguments (empty strings yield defaults):
 *      stashes     list of stash names to access during script execution
 *      script      shell script to run
 *      label       label to use when running the script
 */
Map call(Map kwargs = [:]) {
    long startDate = System.currentTimeMillis()
    List stashes = kwargs.get('stashes', [])
    String script = kwargs.get('script', '')
    String label = kwargs.get('label', "Run ${script} with stashes")

    Integer stashCount = 0
    stashes.each { stash ->
        try {
            unstash stash
            println("Successfully un-stashed ${stash}.")
            stashCount++
        } catch (hudson.AbortException ex) {
            println("Ignoring failure to unstash ${stash}.  Perhaps the stage was skipped?")
        }
    }

    Integer returnCode = 255
    if (!script) {
        // Nothing to do if no stash exist
        println('No script provided, skipping execution')
        returnCode = 0
    } else if (stashCount < 1) {
        // Nothing to do if no stash exist
        println('No code coverage stashes found, skipping merged code coverage report')
        returnCode = 0
    } else {
        // Run the script
        try {
            sh(script: script, label: label)
            returnCode = 0
        } catch (hudson.AbortException e) {
            // groovylint-disable UnnecessaryGetter
            // groovylint-disable-next-line NoDef, VariableTypeRequired
            def returnCodeValue = (e.getMessage() =~ /\d+$/)
            if (returnCodeValue) {
                returnCode = returnCodeValue[0] as Integer
            }
        }
    }

    // Generate a result for the stage
    long endDate = System.currentTimeMillis()
    Integer runTime = durationSeconds(startDate, endDate)
    String status = 'SUCCESS'
    if (returnCode != 0) {
        status = 'FAILURE'
    }
    Map results = ['result_code': returnCode,
                   'result': status,
                   'start_date': startDate,
                   'end_date': endDate,
                   'runtest_time': runTime]

    String resultsMap = 'resultsMap_' + sanitizedStageName()
    writeYaml file: resultsMap,
              data: results,
              overwrite: true
    stash name: resultsMap,
          includes: resultsMap

    return results
}
