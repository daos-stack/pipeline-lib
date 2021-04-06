// vars/quickFunctional.groovy

/**
 * quickFunctional.groovy
 *
 * quickFunctional variable
 */

/**
 * Method to return true/false if Quick-Functional: true was set
 */

boolean call() {
    return cachedCommitPragma('Quick-Functional').toLowerCase() == 'true'
}