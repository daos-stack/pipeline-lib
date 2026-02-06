/* groovylint-disable DuplicateStringLiteral, IfStatementCouldBeTernary */
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
    if (params.BuildType && params.BuildType != '') {
        return 'BUILD_TYPE=' + params.BuildType
    }
    if (cachedCommitPragma('faults-enabled', 'true').toLowerCase() == 'true' &&
         !releaseCandidate()) {
        return 'BUILD_TYPE=dev'
    }
    return 'BUILD_TYPE=release'
}
