// vars/hwDistroTarget2.groovy

/**
 * hwDistroTarget2.groovy
 *
 * hwDistroTarget2 variable
 */

/**
 * Method to return the distro target and version (as a list) for a given stage
 */

// I'd love to use a more explicit
// String, String hw_distro(String size) here but it chokes Jenkins (at
// least)
List call(String size) {
    // Possible values:
    // leap15
    // centos7
    // el8
    // NOTE: the default distro does not get set, here below if the DAOS Jenkinsfile has a CI_HARDWARE_DISTRO parameter
    String distro = cachedCommitPragma('EL8-target', 'el' +
                                       cachedCommitPragma('EL8-version',
                                                          distroVersion('el8')))
    if (params.CI_HARDWARE_DISTRO) {
        distro = params.CI_HARDWARE_DISTRO
    }
    distro = cachedCommitPragma('Func-hw-test-' + size + '-distro',
                              cachedCommitPragma('Func-hw-test-distro', distro))
    return (distro =~ /([a-z]+)(.*)/)[0][1..2]
}

List call() {
    if (env.STAGE_NAME.contains('Hardware')) {
        return hwDistroTarget2(env.STAGE_NAME[env.STAGE_NAME.lastIndexOf(" ")
                                              + 1..-1].toLowerCase())
    }
    return (parseStageInfo()['target'] =~ /([a-z]+)(.*)/)[0][1..2]
}
