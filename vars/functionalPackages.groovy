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
    String _distro = distro ?: parseStageInfo()['target']
    String daos_pkgs = getDAOSPackages(_distro, next_version.toString(), add_daos_pkgs)
    String pkgs = ''
    if (fileExists('ci/functional/required_packages.sh')) {
        pkgs = sh(script: "ci/functional/required_packages.sh ${_distro} " +
                          client_ver,
                  returnStdout: true)
    } else {
        echo "ci/functional/required_packages.sh doesn't exist.  " +
             'Hopefully the daos-tests packages have the dependencies configured.'
    }

    if (_distro.startsWith('leap') || _distro.startsWith('sles') ||
        _distro.startsWith('el') || _distro.startsWith('centos') ||
        _distro.startsWith('rocky') || _distro.startsWith('almalinux') ||
        _distro.startsWith('rhel') || _distro.startsWith('ubuntu')) {
        return daos_pkgs + ' ' + pkgs
    }

    error "functionalPackages not implemented for ${_distro}"

    return ''
}
