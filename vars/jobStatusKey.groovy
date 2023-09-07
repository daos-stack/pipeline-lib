// vars/jobStatusKey.groovy

/**
 * jobStatusKey.groovy
 *
 * Get a job status key.
 *
 * @param name String name of the stage
 * @return a String to be used as a Map key w/o spaces
 */
String call(String name) {
    return name.replaceAll('[ .]', '_')
}
