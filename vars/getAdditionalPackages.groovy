// vars/getAdditionalPackages.groovy

/**
 *
 * getAdditionalPackages.groovy
 *
 * Get the additional packages for the functional test stages based on the provider and
 * whether or not bullseye reporting is enabled.
 *
 * @ param ucx              whether or not to include UCX packages
 * @ param bullseye         whether or not the packages are bullseye versioned
 * @ return a String of space-separated package names
 */
String call(Boolean ucx=false, Boolean bullseye=false) {
    String packages = ''
    if (ucx) {
        packages += ' mercury-ucx'
    } else {
        packages += ' mercury-libfabric'
    }
    if (bullseye) {
        packages += ' bullseye'
    }
    return packages.trim()
}
