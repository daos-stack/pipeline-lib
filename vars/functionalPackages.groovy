// vars/functionalPackages.groovy

/**
 * functionalPackages.groovy
 *
 * functionalPackages variable
 */

/**
 * Method to return the list of packages to install for functional testing
 */

String call(Integer client_ver, String next_version) {
    return functionalPackages(hwDistroTarget(), client_ver, next_version)
}

String call(String distro, Integer client_ver, String next_version) {
    String daos_pkgs = getDAOSPackages(distro, next_version)
    if (!fileExists('ci/functional/required_packages.sh')) {
        echo "ci/functional/required_packages.sh doesn't exist.  " +
             "Hopefully the daos-tests package has the dependencies configured."
        return
    }

    String pkgs = sh(script: "ci/functional/required_packages.sh ${distro} " +
                             client_ver,
                        returnStdout: true)

    if (distro.startsWith('leap') || distro.startsWith('opensuse') ||
        distro.startsWith('el') || distro.startsWith('centos') ||
        distro.startsWith('ubuntu20')) {
        return daos_pkgs + ' ' + pkgs
    }
    error 'functionalPackages not implemented for ' + distro
}
