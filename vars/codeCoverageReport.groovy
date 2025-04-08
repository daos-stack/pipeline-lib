// vars/codeCoverageReport.groovy

/**
 * // vars/codeCoverageReport.groovy
 *
 * Generate a merged code coverage report from any individual stage's code coverage report.
 *
 * @param kwargs Map containing the following optional arguments (empty strings yield defaults):
 *      stashes     list of stash names including code coverage results from other stages
 *      script      shell script to run to generate the merged code coverage report
 *      label       label to use when running the script
 */
 Integer call(Map kwargs = [:]) {
    List stashes = kwargs.get('stashes', [])
    String script = kwargs.get('script', 'ci/code_coverage_report.sh')
    String label = kwargs.get('label', 'Code Coverage Report')
    Integer stash_count = 0
    Integer return_code = 255

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

    // Nothing to do if no stash exist
    if (stash_count < 1) {
        println('No code coverage stashes found, skipping merged code coverage report')
        return 0
    }

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
    return return_code
 }
