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
    if (!fileExists('ci/unit/required_packages.sh')) {
        echo "ci/unit/required_packages.sh doesn't exist.  " +
             "Hopefully the dependencies are installed some other way."
        return
    }

    Map stage_info = parseStageInfo()

    if (stage_info['target'].startsWith('centos')) {
        if (quickBuild()) {
            // the script run below will read from this file
            unstash stage_info['target'] + '-required-mercury-rpm-version'
        }

        return sh(script: "ci/unit/required_packages.sh " +
                          stage_info['target'] + " " +
                          String.valueOf(quickBuild()),
                  returnStdout: true)
    } else {
        error 'unitPackages not implemented for ' + stage_info['target']
    }
}
