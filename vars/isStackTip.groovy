/* groovylint-disable DuplicateStringLiteral */
// vars/isStackTip.groovy

/**
 * isStackTip.groovy
 *
 * isStackTip variable
 */

/**
 * Determine whether this build needs the full pull request verification.
 *
 * A stack merges atomically up to and including the pull request being merged,
 * so the top pull request of a stack contains the complete set of changes that
 * will land.  Verifying it verifies the whole stack, which means the layers
 * below it do not need to repeat the expensive testing.
 *
 * @return true when this is not a stacked pull request, or when it is the top
 *         pull request of its stack; false for a mid-stack pull request
 */
boolean call() {
    if (!paramsValue('CI_STACK_TIP_ONLY', true)) {
        return true
    }
    if (cachedCommitPragma('Skip-stack-optimization').toLowerCase() == 'true') {
        return true
    }

    Map stack = prStack()
    if (!stack) {
        return true
    }

    // GitHub numbers stack positions from 1 at the bottom, so the top pull
    // request is the one whose position is the size of the stack.
    return stack['position'] == stack['size']
}
