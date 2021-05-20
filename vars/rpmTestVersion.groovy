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
    if (env.CI_NOBUILD == "true") {
        return env.CI_RPM_TEST_VERSION
    }
    return cachedCommitPragma('RPM-test-version')
}
