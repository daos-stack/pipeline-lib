/* groovylint-disable VariableName */
// vars/unitPackages.groovy
/*
 * Copyright 2020-2024 Intel Corporation
 * Copyright 2025-2026 Hewlett Packard Enterprise Development LP
 */

/**
 * unitPackages step method to return the list of Unit Testing packages
 *
 * @param config Map of parameters passed
 *
 * config['target']            Target distribution, such as 'el8',
 *                             'el9', 'leap15'.  Default based on parsing
 *                             environment variables for the stage.
 */

String call(Map config = [:]) {
    String script = 'ci/unit/required_packages.sh'
    if (!fileExists(script)) {
        echo "${script} doesn't exist.  " +
             'Hopefully the dependencies are installed some other way.'
        return
    }

    Map stage_info = parseStageInfo(config)
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
