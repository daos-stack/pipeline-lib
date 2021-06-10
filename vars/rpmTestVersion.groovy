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
    if (params.CI_NOBUILD) {
        return params.CI_RPM_TEST_VERSION
    }
    return cachedCommitPragma('RPM-test-version')
}
