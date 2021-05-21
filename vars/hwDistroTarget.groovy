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
    return cachedCommitPragma('Func-hw-test-' + size + '-distro',
                              cachedCommitPragma('Func-hw-test-distro', 'centos7'))
}

String call() {
    if (env.STAGE_NAME.contains('Hardware')) {
        return hw_distro(env.STAGE_NAME[20..-1].toLowerCase())
    }
    return parseStageInfo()['target']
}