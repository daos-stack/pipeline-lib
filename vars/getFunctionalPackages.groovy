// vars/getFunctionalPackages.groovy

/**
 * getFunctionalPackages.groovy
 *
 * Get the packages to install in the functional test satge.
 *
 * @param distro            functional test stage distro
 * @param nextVersion       next daos package version
 * @param daosPackages      daos packages to install (with a version)
 * @param otherPackages     space-separated string of additional non-daos packages to install
 * @param bullseye          option to include the '.bullseye' extension to the daos package version
 * @return a scripted stage to run in a pipeline
 */

String call(String nextVersion, String otherPackages, Boolean bullseye=false) {
    String distro = parseStageInfo()['target']
    return getFunctionalPackages(distro, nextVersion, null, otherPackages, bullseye)
}

String call(String nextVersion, Boolean ucx=false, Boolean bullseye=false) {
    String distro = parseStageInfo()['target']
    String otherPackages = getAdditionalPackages(ucx, bullseye)
    return getFunctionalPackages(distro, nextVersion, null, otherPackages, bullseye)
}

String call(String distro, String nextVersion, String daosPackages, String otherPackages,
            Boolean bullseye=false) {
    String version = daosPackagesVersion(distro, nextVersion)
    String packages = ''

    if (daosPackages) {
        packages += daosPackages
    } else {
        packages += 'daos{,-{client,tests,server,serialize,tests-internal}}'
    }

    // Add the build-specific version to the daos packages
    if (version) {
        if (distro.startsWith('ubuntu20')) {
            packages += "=${version}"
        } else {
            packages += "-${version}"
        }
        if (bullseye) {
            packages += '.bullseye'
        }
    }

    // Add non-daos packages
    if (otherPackages) {
        packages += " ${otherPackages}"
    }

    println("getFunctionalPackages: distro=${distro}, version=${version}, bullseye=${bullseye}, otherPackages=${otherPackages} => ${packages}")

    return packages
}
