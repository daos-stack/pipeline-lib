/* groovylint-disable VariableName */
// vars/unitPackages.groovy

/**
 * unitPackages.groovy
 *
 * unitPackages variable
 */

/**
 * Method to return the list of Unit Testing packages
 *
 * args['image_version']
 */

String call(Map args = [:]) {
    String script = 'ci/unit/required_packages.sh'
    if (!fileExists(script)) {
        echo "${script} doesn't exist.  " +
             'Hopefully the dependencies are installed some other way.'
        return
    }
    String target = ''

    Map stage_info = parseStageInfo()
    if (args.isEmpty()) {
        // TODO: This case is kept only to support callers that have not yet been updated.
        // It will be removed once it is no longer needed.
        target = stage_info['target']
    } else {
        target = args['image_version']
    }

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
