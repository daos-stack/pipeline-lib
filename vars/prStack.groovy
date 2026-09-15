/* groovylint-disable DuplicateStringLiteral */
// vars/prStack.groovy

/**
 * prStack.groovy
 *
 * prStack variable
 */

import groovy.transform.Field

/* groovylint-disable-next-line CompileStatic */
@Field static Map pr_stack_cache = [:]

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
 * Query GitHub for the stack the pull request being built belongs to.
 *
 * GitHub evaluates every pull request in a stack against the base of the stack
 * rather than the branch it directly targets, so a job needs the stack metadata
 * to know both what it is really landing on and where it sits in the stack.
 *
 * This intentionally fails open: if the stack cannot be determined for any
 * reason an empty Map is returned and callers behave as they did before
 * stacked pull requests existed.
 *
 * @param config Map with an optional 'clear' key to drop the cached value
 * @return Map with 'number', 'size', 'position', 'base_ref' and 'base_sha'
 *         keys, or an empty Map when the build is not a stacked pull request
 */
Map call(Map config = [:]) {
    if (config['clear']) {
        pr_stack_cache.clear()
        return [:]
    }

    if (!env.CHANGE_ID) {
        // Not a pull request build, so it cannot be part of a stack.
        return [:]
    }

    String cache_key = "${env.JOB_NAME}:${env.CHANGE_ID}"
    if (pr_stack_cache.containsKey(cache_key)) {
        return pr_stack_cache[cache_key]
    }

    Map stack = [:]
    try {
        stack = fetchStack()
    } catch (java.lang.Exception e) {
        // Never fail a build because the stack could not be looked up.
        println("prStack: unable to determine stack membership, assuming the " +
                "pull request is not stacked: ${e}")
        stack = [:]
    }

    pr_stack_cache[cache_key] = stack
    return stack
}

Map fetchStack() {
    String repo = ghRepo()
    if (!repo) {
        println("prStack: cannot determine the GitHub repository from JOB_NAME " +
                "'${env.JOB_NAME}'")
        return [:]
    }

    // Only pipeline steps are used here.  prStack() is called while the
    // Jenkinsfile is still being loaded, before any node has been allocated, so
    // anything needing a workspace throws MissingContextVariableException, and
    // the library is sandboxed, so a raw HttpURLConnection needs script
    // approvals that this cannot rely on being in place.
    String response
    withCredentials(githubAccess()) {
        response = httpRequest(url: "https://api.github.com/repos/${repo}/pulls/${env.CHANGE_ID}",
                               httpMode: 'GET',
                               acceptType: 'APPLICATION_JSON',
                               customHeaders: [[name: 'Authorization',
                                                value: "Bearer ${env.GH_PASS}",
                                                maskValue: true]],
                               consoleLogResponseBody: false).content
    }

    Map pull_request = readJSON(text: response)
    Map stack = pull_request['stack']
    if (!stack) {
        // Not part of a stack.  This is the common case.
        return [:]
    }

    Map result = ['number': stack['number'],
                  'size': stack['size'],
                  'position': stack['position'],
                  'base_ref': stack['base']['ref'],
                  'base_sha': stack['base']['sha']]

    println("prStack: PR ${env.CHANGE_ID} is layer ${result['position']} of " +
            "${result['size']} in stack ${result['number']} onto " +
            "${result['base_ref']}")
    return result
}
