// vars/getFunctionalPackages.groovy

/**
 * getFunctionalPackages.groovy
 *
 * Get the packages to install in the functional test satge.
 *
 * @param distro            functional test stage distro
 * @param nextVersion       next daos package version
 * @param addDaosPackages   additional daos-* version packages to install
 * @param versionExt        optional daos RPM version extension
 * @param otherPackages     space-separated string of additional non-daos packages to install
 * @return a scripted stage to run in a pipeline
 */

String call(String nextVersion, String daosPackages) {
    String distro = parseStageInfo()['target']
    return getFunctionalPackages(distro, nextVersion, daosPackages, null, null)
}

String call(String nextVersion, String daosPackages, String otherPackages) {
    String distro = parseStageInfo()['target']
    return getFunctionalPackages(distro, nextVersion, daosPackages, otherPackages, null)
}

String call(String distro, String nextVersion, String daosPackages, String otherPackages,
           String versionExt) {
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
        if (versionExt) {
            packages += versionExt
        }
    }

    // Add non-daos packages
    if (otherPackages) {
        packages += " ${otherPackages}"
    }

    return packages
}
