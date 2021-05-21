// vars/rpmTestVersion.groovy

/**
 * rpmTestVersion.groovy
 *
 * rpmTestVersion variable
 */

/**
 * Method to return the RPM-test-version pragma
 */

boolean call() {
    return cachedCommitPragma('RPM-test-version')
}