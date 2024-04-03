// vars/recordIssues.groovy

/**
 * recordIssues.groovy
 *
 * Wrapper for recordIssues for SRE-2145
 */

//groovylint-disable DuplicateStringLiteral
void call(Map config = [:]) {
    String cbResult = currentBuild.result
    String cbcResult = currentBuild.currentResult
    config.remove('ignoreFailedBuilds')
    steps.recordIssues(config)
    if (cbResult != currentBuild.result) {
        println "The recordIssues plugin changed result to ${currentBuild.result}."
    }
    if (cbcResult != currentBuild.currentResult) {
        println('The recordIssues plugin changed currentResult to ' +
                currentBuild.currentResult + '.')
    }
}
