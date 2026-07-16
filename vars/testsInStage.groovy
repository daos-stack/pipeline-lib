/* groovylint-disable CatchException, DuplicateNumberLiteral, DuplicateStringLiteral */
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
    println("[${env.STAGE_NAME}] Determining if tests w/ '${tags}' tags exist for this stage")
    if (env.UNIT_TEST && env.UNIT_TEST == 'true') {
        return true
    }

    if (!fileExists('src/tests/ftest')) {
        println("[${env.STAGE_NAME}] src/tests/ftest does not exist, assuming tests exist")
        return true
    }
    try {
        directory('src/tests/ftest') {
            if (fileExists('list_tests.py')) {
                return sh(
                    label: 'Run list_tests.py',
                    script: "./list_tests.py ${tags}",
                    returnStatus: true) == 0
            }
            if (fileExists('launch.py')) {
                /* groovylint-disable-next-line UnnecessaryGetter */
                String verbose = isPr() ? '--verbose ' : ''
                return sh(
                    label: 'Run launch.py',
                    script: "./launch.py --list ${verbose} ${tags}",
                    returnStatus: true) == 0
            }
            println("[${env.STAGE_NAME}] Neither list_tests.py or launch.py found")
        }
    } catch (Exception e) {
        println("[${env.STAGE_NAME}] Caught exception in try: ${e}")
    }
    println("[${env.STAGE_NAME}] Could not determine if tests exist, assuming they do.")
    return true
}
