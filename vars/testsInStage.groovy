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
    if (env.BRANCH_NAME.startsWith('weekly-testing')) {
        /* This doesn't actually work on weekly-ltestin branches due to a lack
         * src/test/ftest/launch.py (and friends).  We could probably just
         * check that out from the branch we are testing against (i.e. master,
         * release/*, etc.) but let's save that for another day
         */
        return true
    }

    return sh(label: "Get test list",
              script: """if [ \${UNIT_TEST:-false} ]; then
                             exit 0
                         fi
                         cd src/tests/ftest
                         ./list_tests.py """ + parseStageInfo()['test_tag'],
              returnStatus: true) == 0
}