// vars/archiveArtifacts.groovy
/**
 * run.groovy
 *
 * Wrapper for archiveArtifacts step to allow for skipped tests.
 */

//groovylint-disable DuplicateStringLiteral
void call(Map config = [:]) {
    if (env.NO_CI_TESTING != 'true' ||
            cachedCommitPragma('Skip-Test') == 'true') {
        config['allowEmptyArchive'] = true
    }
    steps.archiveArtifacts(config)
}
