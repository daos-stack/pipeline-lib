/* groovylint-disable VariableName */
// vars/unitPackages.groovy

/**
 * unitPackages.groovy
 *
 * unitPackages variable
 */

/**
 * Method to return the list of Unit Testing packages
 */

String call() {
    String script = 'ci/unit/required_packages.sh'
    if (!fileExists(script)) {
        echo "${script} doesn't exist.  " +
             'Hopefully the dependencies are installed some other way.'
        return
    }

    Map stage_info = parseStageInfo()
    String target = stage_info['target']
    boolean quick_build = quickBuild()

    if (target.startsWith('centos') || target.startsWith('el')) {
        if (quick_build) {
            // the script run below will read from this file
            unstash target + '-required-mercury-rpm-version'
        }

        return sh(script: "${script} ${target} " +
                          String.valueOf(quick_build),
                  returnStdout: true)
    }
    error 'unitPackages not implemented for ' + target
    return
}
