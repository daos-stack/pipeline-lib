// vars/getFunctionalPackages.groovy

/**
 * getFunctionalPackages.groovy
 *
 * Get the packages to install in the functional test satge.
 *
 * @param daosPackages      daos packages to install (with a version)
 * @param otherPackages     space-separated string of additional non-daos packages to install
 * @param bullseye          option to include the '.bullseye' extension to the daos package version
 * @return a scripted stage to run in a pipeline
 */

String call(String otherPackages, Boolean bullseye=false) {
    return getFunctionalPackages(null, otherPackages, bullseye)
}

String call(Boolean ucx=false, Boolean bullseye=false) {
    String otherPackages = getAdditionalPackages(ucx, bullseye)
    return getFunctionalPackages(null, otherPackages, bullseye)
}

String call(String daosPackages, String otherPackages, Boolean bullseye=false) {
    String packages = ''

    if (daosPackages) {
        packages += daosPackages
    } else if (bullseye) {
        packages += 'daos-bullseye{,-{client,tests,server,serialize,tests-internal}}'
    } else {
        packages += 'daos{,-{client,tests,server,serialize,tests-internal}}'
    }

    // Add non-daos packages
    if (otherPackages) {
        packages += " ${otherPackages}"
    }

    println("getFunctionalPackages(${daosPackages}, ${otherPackages}, ${bullseye}) => ${packages}")

    return packages
}
