// vars/rpmlintMockResults.groovy

/**
 * rpmlintMockResults.groovy
 *
 * rpmlintMockResults variable
 */


 /**
 * Method to run rpmlint on the result of mock build
 *
 *
 * @param config Mock configuration name to check in
 * @param allow_errors Whether errors are allowd.  Defaults to false.
 */
String call(String config, Boolean allow_errors=false) {

    String output = sh(label: 'RPM Lint built RPMs',
                       script: "rpmlint \$(ls /var/lib/mock/${config}/result/*.rpm | grep -v -e -debuginfo) || exit 0",
                       returnStdout: true).trim()

    int result = sh(label: 'Analyze rpmlint output',
                    script: """read e w b < <(echo \"${output}\" | sed -nEe '\$s/.*([0-9]+) errors, ([0-9]+) warnings, ([0-9]+) badness;.*/\\1 \\2 \\3/p')
                               if [ "\$e" -gt 0 ]; then
                                   exit 2
                               elif [ "\$w" -gt 0 ]; then
                                   exit 1
                               fi
                               exit 0""",
                    returnStatus: true)

    catchError(stageResult: 'UNSTABLE', buildResult: 'SUCCESS') {
        if (result == 2 && allow_errors) {
            //error('RPM Lint found errors, but allow_errors is: ' + allow_errors +'\n' + output)
            echo('RPM Lint found errors, but allow_errors is: ' + allow_errors +'\n' + output)
        } else if (result == 1) {
            //error('RPM Lint found warnings:\n' + output)
            echo('RPM Lint found warnings:\n' + output)
        }
        return
    }

    catchError(stageResult: 'UNSTABLE', buildResult: 'UNSTABLE') {
        if (result == 2 && ! allow_errors) {
            //error('RPM Lint found errors:\n' + output)
            echo('RPM Lint found errors:\n' + output)
        }
        return
    }

    // the returns above in the catchError() blocks don't acutally seem to work
    if (result == 0) {
        echo('RPM Lint output:\n' + output)
    }
}