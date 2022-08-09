/* groovylint-disable ParameterName, VariableName */
// vars/functionalPackages.groovy

/**
 * functionalPackages.groovy
 *
 * functionalPackages variable
 */

/**
 * Method to return the list of packages to install for functional testing
 */

String call(Integer client_ver, BigDecimal next_version) {
    return functionalPackages(client_ver, next_version.toString(), null)
}

String call(Integer client_ver, BigDecimal next_version, String add_daos_pkgs) {
    return functionalPackages(client_ver, next_version.toString(), add_daos_pkgs)
}

String call(Integer client_ver, String next_version) {
    return functionalPackages(client_ver, next_version, null)
}

String call(Integer client_ver, String next_version, String add_daos_pkgs) {
    return functionalPackages(parseStageInfo()['target'], client_ver, next_version, add_daos_pkgs)
}

String call(String distro, Integer client_ver, String next_version) {
    return functionalPackages(distro, client_ver, next_version, null)
}

String call(String distro, Integer client_ver, String next_version, String add_daos_pkgs) {
    String daos_pkgs = getDAOSPackages(distro, next_version.toString(), add_daos_pkgs)
    if (!fileExists('ci/functional/required_packages.sh')) {
        echo "ci/functional/required_packages.sh doesn't exist.  " +
             'Hopefully the daos-tests package has the dependencies configured.'
        return
    }

    String pkgs = sh(script: "ci/functional/required_packages.sh ${distro} " +
                             client_ver,
                        returnStdout: true)

    if (distro.startsWith('leap15') ||
        distro.startsWith('el7') || distro.startsWith('centos7') ||
        distro.startsWith('el8') || distro.startsWith('centos8') ||
        distro.startsWith('rocky8') || distro.startsWith('almalinux8') ||
        distro.startsWith('rhel8') || distro.startsWith('ubuntu20')) {
        return daos_pkgs + ' ' + pkgs
    }

    error 'functionalPackages not implemented for ' + distro

    return ""
}
