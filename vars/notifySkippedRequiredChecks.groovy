/* groovylint-disable DuplicateStringLiteral */
// vars/notifySkippedRequiredChecks.groovy

/**
 * notifySkippedRequiredChecks.groovy
 *
 * notifySkippedRequiredChecks variable
 */

/**
 * Return the GitHub org/repo that this job is building, i.e. 'daos-stack/daos'.
 */
String ghRepo() {
    // JOB_NAME=daos-stack/daos/PR-65
    String[] jobNameParts = env.JOB_NAME.split('/')
    if (jobNameParts.length < 3) {
        return ''
    }
    return jobNameParts[jobNameParts.length - 3] + '/' + jobNameParts[jobNameParts.length - 2]
}

/**
 * Query GitHub for the status check contexts that are required to merge into a
 * branch.
 *
 * This intentionally fails open: a branch with no protection, a token that
 * cannot read the protection settings and an unreachable API all return an
 * empty List, which makes the caller a no-op.
 *
 * @param branch the branch to read the protection settings of
 * @return List of required status check context names
 */
List<String> requiredContexts(String branch) {
    String repo = ghRepo()
    if (!repo) {
        println('notifySkippedRequiredChecks: cannot determine the GitHub repository ' +
                "from JOB_NAME '${env.JOB_NAME}'")
        return []
    }

    String url = "https://api.github.com/repos/${repo}/branches/" +
                 java.net.URLEncoder.encode(branch, 'UTF-8') +
                 '/protection/required_status_checks/contexts'

    String response
    withCredentials(githubAccess()) {
        response = httpRequest(url: url,
                               httpMode: 'GET',
                               acceptType: 'APPLICATION_JSON',
                               customHeaders: [[name: 'Authorization',
                                                value: "Bearer ${env.GH_PASS}",
                                                maskValue: true]],
                               consoleLogResponseBody: false).content
    }

    return readJSON(text: response)
}

/**
 * Map a required status check context back to the pipeline stage that would
 * publish it.
 *
 * Contexts published by this pipeline are the stage name prefixed with the kind
 * of stage, i.e. the 'Unit Test' stage publishes 'test/Unit Test' and the
 * 'Build on EL 9' stage publishes 'build/Build on EL 9'.  Contexts that are not
 * published by Jenkins, such as those from GitHub Actions, have no prefix and
 * are left alone.
 *
 * @param context the required status check context
 * @return the stage name that publishes it, or an empty String
 */
String contextStage(String context) {
    for (String prefix in ['test/', 'build/']) {
        if (context.startsWith(prefix)) {
            return context.substring(prefix.length())
        }
    }
    return ''
}

/**
 * Publish a successful status for every required check whose stage was
 * deliberately not run.
 *
 * A status check context is published from inside the body of the stage that
 * produces it, so a stage that does not run never reports and GitHub blocks the
 * pull request forever waiting for it.  That is fine for a stage skipped by a
 * commit pragma, where the author has opted out knowingly, but it makes a pull
 * request in the middle of a stack unmergeable: the verification it needs is
 * deliberately being run on the tip of the stack instead, so nothing will ever
 * report those contexts against it.
 *
 * Publish them here instead, with a description saying why they are green.
 *
 * Call this before the stages run.  Statuses are last-write-wins per context,
 * so a stage that does run overwrites the status published here with its real
 * result, including a real failure.  Calling it afterwards would mask genuine
 * failures.
 *
 * This intentionally fails open: it never fails a build.
 *
 * @param config Map of parameters passed
 *
 * config['skipped_stages'] List of stage names that will not be run.
 * config['target_branch'] Branch the pull request lands on.  Defaults to
 *                         targetBranch().
 * config['description'] Description to publish with each status.
 */
void call(Map config = [:]) {
    List<String> skipped = config.get('skipped_stages', [])
    if (!skipped) {
        return
    }

    if (!env.CHANGE_ID) {
        // Only a pull request has status checks to satisfy.
        return
    }

    if (env.DAOS_JENKINS_NOTIFY_STATUS == null) {
        println('notifySkippedRequiredChecks: Jenkins not configured to notify SCM ' +
                'repository of builds')
        return
    }

    String branch = config.get('target_branch', targetBranch())
    String description = config.get('description', 'Stage was not run')

    List<String> required = []
    try {
        required = requiredContexts(branch)
    } catch (java.lang.Exception e) {
        // Never fail a build because the required checks could not be read.
        println('notifySkippedRequiredChecks: unable to read the required status checks ' +
                "of ${branch}, not publishing any statuses: ${e}")
        return
    }

    for (String context in required) {
        String stage = contextStage(context)
        if (!stage || !(stage in skipped)) {
            continue
        }
        println("notifySkippedRequiredChecks: publishing success for '${context}': " +
                description)
        scmNotify(context: context, description: description, status: 'SUCCESS')
    }
}
