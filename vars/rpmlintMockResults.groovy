/* groovylint-disable DuplicateNumberLiteral, DuplicateStringLiteral */
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
 * @param allow_errors Whether errors are allowed.  Defaults to false.
 */
/* groovylint-disable-next-line ParameterName */
void call(String config, Boolean allow_errors=false, Boolean skip_rpmlint=false, String make_args='') {
    if (skip_rpmlint || config == 'not_applicable') {
        return
    }

    String chdir = '.'
    if (fileExists('utils/rpms/Makefile')) {
        chdir = 'utils/rpms/'
    }
    int result = sh(label: 'RPM Lint built RPMs',
                    script: 'cd ' + chdir + '\n' +
                             /* groovylint-disable-next-line GStringExpressionWithinString */
                             '''name=$(make ''' + make_args + ''' show_NAME)
                                if [ -f "$name".rpmlintrc ]; then
                                    rpmlint_args=(-r "$name".rpmlintrc)
                                fi
                                rpmlint --ignore-unused-rpmlintrc "${rpmlint_args[@]}" ''' +
                               '$(ls /var/lib/mock/' + config + '/result/*.rpm | ' +
                               'grep -v -e -debuginfo -e debugsource)',
                    returnStatus: true)

    catchError(stageResult: 'UNSTABLE', buildResult: 'SUCCESS') {
        if (result > 0 && allow_errors) {
            error('RPM Lint found errors, but allow_errors is true.')
        }
    }

    catchError(stageResult: 'UNSTABLE', buildResult: 'UNSTABLE') {
        if (result > 0 && !allow_errors) {
            error('RPM Lint found errors.')
        }
    }

    // Print success message if rpmlint exits with 0
    if (result == 0) {
        echo 'RPM Lint passed with no errors or warnings.'
    }
}
