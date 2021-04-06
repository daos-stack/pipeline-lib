// vars/quickBuild.groovy

/**
 * quickBuild.groovy
 *
 * quickBuild variable
 */

/**
 * Method to return true/false if Quick-build: true was set
 */

boolean call() {
    return cachedCommitPragma('Quick-build').toLowerCase() == 'true' ||
           quickFunctional()
}