// vars/sconsFaultsArgs.groovy

/**
 * sconsFaultsArgs.groovy
 *
 * sconsFaultsArgs variable
 */

/**
 * Method to return the scons args for FI
 */

boolean call() {
    // The default build will have BUILD_TYPE=dev; fault injection enabled
    if (params.TestTag.indexOf("with_fi") >= 0 ||
        (cachedCommitPragma('faults-enabled', 'true').toLowerCase() == 'true' &&
         !releaseCandidate())) {
        return "BUILD_TYPE=dev"
    } else {
        return "BUILD_TYPE=release"
    }
}