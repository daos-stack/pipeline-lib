// vars/hwDistroTarget.groovy

/**
 * hwDistroTarget.groovy
 *
 * hwDistroTarget variable
 */

/**
 * Method to return the distro target for a given stage
 */

String call(String size) {
    (name, version) = hwDistroTarget2(size)
    return name + version
}

String call() {
    (name, version) = hwDistroTarget2()
    return name + version
}
