/* groovylint-disable DuplicateStringLiteral */
// vars/rpmlintMockResults.groovy

/**
 * rpmlintMockResults.groovy
 *
 * rpmlintMockResults variable
 */

 /**
 * Method to run rpmlint on the result of mock build
 *
 * @param config Mock configuration name to check in
 * @param allow_errors Whether errors are allowd.  Defaults to false.
 */
String warnings(String output) {
    return extract(output, 'warnings')
}

String errors(String output) {
    return extract(output, 'errors')
}

String extract(String output, String type) {
    /* groovylint-disable-next-line VariableName */
    String T
    if (type == 'errors') {
        T = 'E'
    } else if (type == 'warnings') {
        T = 'W'
    } else {
        error("Unsupported type ${type} passed to extract()")
    }

    return sh(label: "Extract ${type} from rpmlint output",
              script: "echo \"${output}\" | grep ': ${T}: '",
              returnStdout: true).trim()
}

/* groovylint-disable-next-line ParameterName */
void call(String config, Boolean allow_errors=false, Boolean skip_rpmlint=false) {
    if (skip_rpmlint || config == 'not_applicable') {
        return
    }

    String chdir = '.'
    if (fileExists('utils/rpms/Makefile')) {
        chdir = 'utils/rpms/'
    }
    String output = sh(label: 'RPM Lint built RPMs',
                       /* groovylint-disable-next-line GStringExpressionWithinString */
                       script: 'cd ' + chdir + '''
                                name=$(make show_NAME)
                                if [ -f "$name".rpmlintrc ]; then
                                    rpmlint_args=(-r "$name".rpmlintrc)
                                fi
                                rpmlint --ignore-unused-rpmlintrc "${rpmlint_args[@]}" ''' +
                               '$(ls /var/lib/mock/' + config + '/result/*.rpm | ' +
                               'grep -v -e -debuginfo -e debugsource) || exit 0',
                       returnStdout: true).trim()

    int result = sh(label: 'Analyze rpmlint output',
                    script: "read e w b < <(echo \"${output}\" | " +
                          """sed -nEe '\$s/.*([0-9]+) errors, ([0-9]+) warnings, ([0-9]+) badness;.*/\\1 \\2 \\3/p')
                               if [ "\$e" -gt 0 ]; then
                                   exit 2
                               elif [ "\$w" -gt 0 ]; then
                                   exit 1
                               fi
                               exit 0""",
                    returnStatus: true)

    catchError(stageResult: 'UNSTABLE', buildResult: 'SUCCESS') {
        if (result == 2 && allow_errors) {
            error('RPM Lint found errors, but allow_errors is ' + allow_errors + ':\n' +
                  errors(output) + '\n\n' +
                  'And also found additional warnings that it would be nice to fix:\n' + warnings(output))
        } else if (result == 1) {
            error('RPM Lint found warnings:\n' + warnings(output))
        }
        return
    }

    catchError(stageResult: 'UNSTABLE', buildResult: 'UNSTABLE') {
        if (result == 2 && !allow_errors) {
            error('RPM Lint found errors:\n' + errors(output) + '\n\n' +
                  'And also found additional warnings that it would be nice to fix:\n' + warnings(output))
        }
        return
    }

    // the returns above in the catchError() blocks don't acutally seem to work
    if (result == 0) {
        echo('RPM Lint output:\n' + output)
    }
}
