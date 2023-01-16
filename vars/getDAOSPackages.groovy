/* groovylint-disable ParameterName, VariableName */
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
    String _add_daos_pkgs = ''
    if (add_daos_pkgs) {
        _add_daos_pkgs = ',-' + add_daos_pkgs
    }

    String pkgs
    if (env.TEST_RPMS == 'true') {
        if (distro.startsWith('ubuntu')) {
            pkgs = 'daos{,-{client,tests,server}' + _add_daos_pkgs + '}'
        } else {
            pkgs = 'daos{,-{client,tests,server,serialize}' + _add_daos_pkgs + '}'
        }
    } else {
        pkgs = 'daos{,-client}'
    }

    if (!distro.startsWith('ubuntu')) {
        String version = daosPackagesVersion(distro, next_version)

        if (version != '') {
            pkgs += '-' + version
        }
    }
    return pkgs
}
