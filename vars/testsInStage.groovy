/* groovylint-disable DuplicateStringLiteral */
// vars/testsInStage.groovy

/**
 * testsInStage.groovy
 *
 * testsInStage variable
 */

/**
 * Method to return true/false if the stage has tests that match the tags
 */

boolean call() {
    return sh(label: 'Get test list',
              /* groovylint-disable-next-line GStringExpressionWithinString */
              script: '''trap 'echo "Got an unhandled error, exiting as if a match was found"; exit 0' ERR
                         if ${UNIT_TEST:-false}; then
                             exit 0
                         fi
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
                             if ./list_tests.py ''' + parseStageInfo()['test_tag'] + '''; then
                                 exit 0
                             fi
                         else
                             if ./launch.py --list ''' + parseStageInfo()['test_tag'] + '''; then
                                 exit 0
                             fi
                         fi
                         trap '' ERR
                         exit 1''',
              returnStatus: true) == 0
}
