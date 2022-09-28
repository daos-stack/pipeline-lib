// vars/isPr.groovy

/**
 * isPr.groovy
 *
 * isPr variable
 */

/**
 * Method to return whether job is a PR or not
 */

Boolean call() {
    if (params.CI_MORE_FUNCTIONAL_PR_TESTS) {
        return false
    }
    if (cachedCommitPragma('Run-landing-stages') == 'true') {
        return false
    }
    return env.CHANGE_ID != null
}
