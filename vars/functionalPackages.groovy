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
    return functionalPackages(
        clientVersion: client_ver,
        nextVersion: next_version.toString()
    )
}

String call(Integer client_ver, BigDecimal next_version, String add_daos_pkgs) {
    return functionalPackages(
        clientVersion: client_ver,
        nextVersion: next_version.toString(),
        addDaosPkgs: add_daos_pkgs
    )
}

String call(Integer client_ver, String next_version) {
    return functionalPackages(
        clientVersion: client_ver,
        nextVersion: next_version
    )
}

String call(Integer client_ver, String next_version, String add_daos_pkgs) {
    return functionalPackages(
        clientVersion: client_ver,
        nextVersion: next_version,
        addDaosPkgs: add_daos_pkgs
    )
}

String call(String distro, Integer client_ver, String next_version) {
    return functionalPackages(
        distro: distro,
        clientVersion: client_ver,
        nextVersion: next_version
    )
}

String call(String distro, Integer client_ver, String next_version, String add_daos_pkgs) {
    return functionalPackages(
        distro: distro,
        clientVersion: client_ver,
        nextVersion: next_version,
        addDaosPkgs: add_daos_pkgs
    )
}

String call(Map args) {
    String distro = args.get('distro', parseStageInfo()['target'])
    Integer clientVersion = args.get('clientVersion', 1)
    String nextVersion = args.get('nextVersion', '1000').toString()
    String addDaosPkgs = args.get('addDaosPkgs', null)
    String rpmDistribution = args.get('rpmDistribution', null)

    String daos_pkgs = getDAOSPackages(distro, nextVersion, addDaosPkgs, rpmDistribution)
    String pkgs = ''
    if (fileExists('ci/functional/required_packages.sh')) {
        pkgs = sh(script: "ci/functional/required_packages.sh ${distro} " +
                          clientVersion,
                  returnStdout: true)
    } else {
        echo "ci/functional/required_packages.sh doesn't exist.  " +
             'Hopefully the daos-tests packages have the dependencies configured.'
    }

    if (distro.startsWith('leap') || distro.startsWith('sles') ||
        distro.startsWith('el') || distro.startsWith('centos') ||
        distro.startsWith('rocky') || distro.startsWith('almalinux') ||
        distro.startsWith('rhel') || distro.startsWith('ubuntu')) {
        return daos_pkgs + ' ' + pkgs
    }

    error "functionalPackages not implemented for ${distro}"

    return ''
}
