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
    Date startDate = new Date()
    List stashes = kwargs.get('stashes', [])
    String script = kwargs.get('script', '')
    String label = kwargs.get('label', "Run ${script} with stashes")

    Integer stash_count = 0
    stashes.each { stash ->
        try {
            unstash stash
            println("Successfully unstashed ${stash}.")
            stash_count++
        /* groovylint-disable-next-line CatchException */
        } catch (Exception ex) {
            println("Ignoring failure to unstash ${stash}.  Perhaps the stage was skipped?")
        }
    }

    Integer return_code = 255
    if (!script) {
        // Nothing to do if no stash exist
        println('No script provided, skipping execution')
        return_code = 0
    } else if (stash_count < 1) {
        // Nothing to do if no stash exist
        println('No code coverage stashes found, skipping merged code coverage report')
        return_code = 0
    } else {
        // Run the script
        try {
            sh(script: script, label: label)
            return_code = 0
        } catch (hudson.AbortException e) {
            // groovylint-disable UnnecessaryGetter
            // groovylint-disable-next-line NoDef, VariableTypeRequired
            def rc_val = (e.getMessage() =~ /\d+$/)
            if (rc_val) {
                return_code = rc_val[0] as Integer
            }
        }
    }

    // Generate a result for the stage
    Date endDate = new Date()
    Integer runTime = durationSeconds(startDate, endDate)
    String status = 'SUCCESS'
    if (return_code != 0) {
        status = 'FAILURE'
    }
    Map results = ['result_code': return_code,
                   'result': status,
                   'start_date': startDate,
                   'end_date': endDate,
                   'runtest_time': runTime]

    String results_map = 'results_map_' + sanitizedStageName()
    writeYaml file: results_map,
              data: results,
              overwrite: true
    stash name: results_map,
          includes: results_map

    return results
}
