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
    return getDAOSPackages(parseStageInfo()['target'], next_version)
}

String call(String distro, String next_version) {
    return getDAOSPackages(distro, next_version, null)
}

String call(String distro, String next_version, String add_daos_pkgs) {
    if (add_daos_pkgs) {
        add_daos_pkgs = ",-" + add_daos_pkgs
    } else {
        add_daos_pkgs = ""
    }

    String pkgs
    if (env.TEST_RPMS == 'true') {
        pkgs = "daos{,-{client,tests,server,serialize}" + add_daos_pkgs + "}"
    } else {
        pkgs = "daos{,-client}"
    }

    if (distro.startsWith('ubuntu20')) {
        return pkgs + "=" + daosPackagesVersion(distro, next_version)
    }
    return pkgs + "-" + daosPackagesVersion(distro, next_version)
}
