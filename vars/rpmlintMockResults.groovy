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
 */
String call(String config) {

    String output = sh(label: "RPM Lint built RPMs",
                       script: "rpmlint \$(ls /var/lib/mock/${config}/result/*.rpm | grep -v -e -debuginfo)",
                       returnStdout: true).trim()
    println(output)
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
        if (result == 1) {
            error("RPM Lint found warnings:\n" + output)
        }
    }

    catchError(stageResult: 'UNSTABLE', buildResult: 'UNSTABLE') {
        if (result == 2) {
            error("RPM Lint found errors:\n" + output)
        }
    }
}