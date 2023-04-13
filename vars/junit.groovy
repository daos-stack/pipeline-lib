// vars/junit.groovy

/**
 * junit.groovy
 *
 * Wrapper for junit step to allow for skipped tests.
 */

void call(String testResults) {
    Map config = [:]
    config['testResults'] = testResults
    call(config)
}

//groovylint-disable DuplicateStringLiteral
void call(Map config = [:]) {
    if (env.NO_CI_TESTING != null ||
            cachedCommitPragma('Skip-Test') == 'true') {
        config['allowEmptyResults'] = true
    }
    // don't trucate stdio/stdout in JUnit results
    // this actually shouldn't be necessary as stdio/stderr is supposed to
    // be preserved for failed results, however:
    // https://github.com/jenkinsci/junit-plugin/issues/219
    // https://issues.jenkins.io/browse/JENKINS-27931
    config['keepLongStdio'] = true
    String cbResult = currentBuild.result
    String cbcResult = currentBuild.currentResult
    steps.junit(config)
    if (cbResult != currentBuild.result) {
        println "The junit plugin changed result to ${currentBuild.result}."
    }
    if (cbcResult != currentBuild.currentResult) {
        println('The junit plugin changed currentResult to ' +
                currentBuild.currentResult + '.')
    }
}
