/* groovylint-disable DuplicateNumberLiteral */
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
    List result = hwDistroTarget2(size)
    return result[0] + result[1]
}

String call() {
    List result = hwDistroTarget2()
    return result[0] + result[1]
}
