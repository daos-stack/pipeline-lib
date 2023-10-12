// vars/stageAlreadyPassed.groovy

/**
 * stageAlreadyPassed
 *
 * Determine if a restarted functional test stage has already passed in a previous run.
 *
 * @param kwargs Map containing the following optional arguments:
 *      stage_name  name of the stage
 *      postfix     test branch name with '/' replaced by '-'
 * @return a Boolean set to true if thestage was restarted and already passed; false otherwise
 */
Boolean call(Map kwargs = [:]) {
    String stage_name = kwargs.get('stage_name', env.STAGE_NAME)
    String postfix = kwargs.get('postfix', '')

    // Lookup to see if restarted
    /* groovylint-disable-next-line UnnecessaryGetter */
    if (!currentBuild.getBuildCauses().any { cause ->
        cause._class == 'org.jenkinsci.plugins.pipeline.modeldefinition.' +
                        'causes.RestartDeclarativePipelineCause' }) {
        return false
    }

    // Make sure a previous check has been removed.
    String status_file = stageStatusFilename(stage_name, postfix)
    if (fileExists(status_file)) {
        fileOperations([fileDeleteOperation(includes: status_file)])
    }

    String old_build = "${env.BUILD_NUMBER.toInteger() - 1}"

    // First try looking up using copyArtifacts, which requires
    // that the Jenkinsfile give permission for the copy.
    try {
        copyArtifacts projectName: env.JOB_NAME,
                      optional: false,
                      filter: status_file,
                      selector: specific(old_build)
        try {
            String stage_status = readFile(file: status_file).trim()
            if (stage_status == 'SUCCESS') {
                return true
            }
            println('Previous run this stage ended with status ' +
                    "'${stage_status}', so re-running")
        } catch (java.nio.file.NoSuchFileException e) {
            // This should not ever fail, so just collecting diagnostics
            // if the code ever gets here.
            println("readFile failed! ${e}")
            sh label: 'Diagnostic for readFile failure',
               script: 'ls -l *.status',
               returnStatus: true
            if (fileExists(status_file)) {
                println("fileExists found ${status_file}")
            }
        }
    } catch (hudson.AbortException e) {
        println("Informational: copyArtifact could not get artifact ${e}")

        // Try using httpRequests, which does not require a modified
        // Jenkinsfile, but makes assumptions on where Jenkins is
        // currently storing the artifacts for a job.
        // This is for transitioning until all Jenkinsfiles are allowing
        // artifacts to be copied and then can be removed.
        String my_build = "/${env.BUILD_NUMBER}/"
        String prev_build = "/${old_build}/"
        String old_job = env.BUILD_URL.replace(my_build, prev_build)
        String art_url = old_job + 'artifact/' + status_file

        /* groovylint-disable-next-line NoDef, VariableTypeRequired */
        def response = httpRequest(url: art_url,
                                   acceptType: 'TEXT_PLAIN',
                                   httpMode: 'GET',
                                   validResponseCodes: '100:599')

        if (response.status == 200 && response.content == 'SUCCESS') {
            return true
        }
    }
    return false
}
