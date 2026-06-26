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

    if (target.startsWith('centos') || target.startsWith('el')) {
        return sh(script: "${script} ${target}",
                  returnStdout: true)
    }
    error 'unitPackages not implemented for ' + target
    return
}
