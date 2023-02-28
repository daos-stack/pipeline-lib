// vars/publishValgrind.groovy
// groovylint-disable DuplicateNumberLiteral
/**
 * run.groovy
 *
 * Wrapper for publishValgrind step to allow for skipped tests.
 */
//groovylint-disable DuplicateStringLiteral
void call(Map config = [:]) {
    if (env.NO_CI_TESTING != 'true' ||
            commitPragma('Skip-Test') == 'true') {
        config['failBuildOnMissingReports'] = false
    }
    steps.publishValgrind(config)
}
