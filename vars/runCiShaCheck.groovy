// vars/runCiShaCheck.groovy

/**
 * runCiShaCheck.groovy
 *
 * runCiShaCheck pipeline step
 *
 * Verify that a build started by a '/run-ci <sha>' pull request comment is
 * building the commit named in the comment.  A bare '/run-ci' is accepted
 * and builds the current pull request head.  Builds that were not started
 * by a pull request comment are not checked.
 */

import groovy.transform.Field

/* groovylint-disable-next-line CompileStatic */
@Field static final String COMMENT_CAUSE =
    'com.adobe.jenkins.github_pr_comment_build.GitHubPullRequestCommentCause'

/**
 * Return the SHA given in a '/run-ci <sha>' comment, '' for a bare
 * '/run-ci', or null if the comment is not a '/run-ci' command.
 */
String runCiSha(String body) {
    String text = (body ?: '').trim()
    if (!(text ==~ /\/run-ci(\s+[0-9a-fA-F]{7,40})?/)) {
        return null
    }
    return text.replaceFirst(/\/run-ci\s*/, '').toLowerCase()
}

/**
 * Fail the build unless a '/run-ci' comment that caused it is bare or names
 * the commit being built.
 *
 * @param config Map of parameters passed
 *      commit  Commit being built (default: env.GIT_COMMIT, else HEAD)
 */
void call(Map config = [:]) {
    List causes = currentBuild.getBuildCauses(COMMENT_CAUSE)
    if (!causes) {
        return
    }

    List shas = causes.collect { cause -> runCiSha(cause.commentBody as String) }
    if (shas.contains('')) {
        echo "Started by '/run-ci' without a commit SHA; building the current pull request head."
        return
    }

    String commit = (config['commit'] ?: env.GIT_COMMIT ?:
                     sh(label: 'Get commit being built',
                        script: 'git rev-parse HEAD',
                        returnStdout: true)).trim().toLowerCase()
    List approved = shas.findAll { sha -> sha }
    if (approved.any { sha -> commit.startsWith(sha) }) {
        echo "Commit ${commit} matches the '/run-ci <sha>' approval."
        return
    }

    String given = approved ? approved.join(', ') : 'no recognized commit'
    error("'/run-ci' approved ${given}, but the pull request head is ${commit}. " +
          "Review the new commits and comment '/run-ci ${commit}' to build them.")
}
