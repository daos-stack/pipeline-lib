// vars/getDAOSPackages.groovy

/**
 * getDAOSPackages.groovy
 *
 * getDAOSPackages variable
 */

/**
 * Method to return the list of DAOS packages
 */
String call(String next_version) {
    return getDAOSPackages(parseStageInfo()['target'])
}

String call(String distro, String next_version) {
    String pkgs
    if (env.TEST_RPMS == 'true') {
        pkgs = "daos{,-{client,tests,server}}"
    } else {
        pkgs = "daos{,-client}"
    }

    if (distro.startsWith('ubuntu20')) {
        return pkgs + "=" + daosPackagesVersion(distro, next_version)
    }
    return pkgs + "-" + daosPackagesVersion(distro, next_version)
}