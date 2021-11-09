// vars/hwDistroTarget.groovy

/**
 * hwDistroTarget.groovy
 *
 * hwDistroTarget variable
 */

/**
 * Method to return the distro target for a given stage
 */

String hw_distro(String size) {
    // Possible values:
    //'leap15
    //'centos7
    //'centos8
    String distro = cachedCommitPragma('EL7-target', 'centos7')
    if (params.CI_HARDWARE_DISTRO) {
        distro = params.CI_HARDWARE_DISTRO
    }
    return cachedCommitPragma('Func-hw-test-' + size + '-distro',
                              cachedCommitPragma('Func-hw-test-distro', distro))
}

String call() {
    if (env.STAGE_NAME.contains('Hardware')) {
        return hw_distro(env.STAGE_NAME[20..-1].toLowerCase())
    }
    return parseStageInfo()['target']
}

String call(String size) {
    return hw_distro(size)
}
