/* groovylint-disable DuplicateStringLiteral */
// vars/targetBranch.groovy

/**
 * targetBranch.groovy
 *
 * targetBranch variable
 */

/**
 * Return the branch that this build will ultimately land on.
 *
 * For a pull request in a stack, env.CHANGE_TARGET is the branch of the pull
 * request directly below it rather than a landing branch such as master or
 * release/2.8.  GitHub evaluates every pull request in a stack against the base
 * of the stack, so that is what the pipeline needs to use when selecting
 * repositories, package versions and branch specific behaviour.
 *
 * @return the base of the stack for a stacked pull request, otherwise the
 *         target branch of the pull request, otherwise the branch being built
 */
String call() {
    Map stack = prStack()
    if (stack && stack['base_ref']) {
        return stack['base_ref']
    }

    return env.CHANGE_TARGET ? env.CHANGE_TARGET : env.BRANCH_NAME
}
