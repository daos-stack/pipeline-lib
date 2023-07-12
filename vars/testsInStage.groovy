/* groovylint-disable DuplicateStringLiteral */
// vars/testsInStage.groovy

/**
 * testsInStage.groovy
 *
 * Method to return true/false if the stage has tests that match the tags
 *
 * @param tags String of functional test tags to use to check for matching tests
 * @return boolean true if there are tests that match the tags
 */
boolean call(String tags) {
    if (env.UNIT_TEST && env.UNIT_TEST == 'true') {
        println('Unit testing, so exiting "Get test list" with true')
        return true
    }
    return sh(label: 'Get test list',
              /* groovylint-disable-next-line GStringExpressionWithinString */
              script: '''trap 'echo "Got an unhandled error, exiting as if a match was found"; exit 0' ERR
                         # This doesn't actually work on weekly-testing branches due to a lack
                         # src/test/ftest/launch.py (and friends).  We could probably just
                         # check that out from the branch we are testing against (i.e. master,
                         # release/*, etc.) but let's save that for another day and just exit
                         # with a "tests found" for now.
                         if ! cd src/tests/ftest; then
                             echo "src/tests/ftest doesn't exist."
                             echo "Could not determine if tests exist for this stage, assuming they do."
                             exit 0
                         fi
                         if [ -x list_tests.py ]; then
                             if ./list_tests.py ''' + tags + '''; then
                                 exit 0
                             fi
                         else
                             if ./launch.py --list ''' + tags + '''; then
                                 exit 0
                             fi
                         fi
                         trap '' ERR
                         exit 1''',
              returnStatus: true) == 0
}
