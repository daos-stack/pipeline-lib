// vars/publishValgrind.groovy

/**
 * run.groovy
 *
 * Wrapper for publishValgrind step to allow for skipped tests.
 *
 */

def call(Map config = [:]) {
    println "Entering publishedValgrind override."
    def script = '''if [ "${NO_CI_TESTING}" == 'true' ]; then
                        exit 1
                    fi
                    if git show -s --format=%B | grep "^Skip-test: true"; then
                        exit 1
                    fi
                    exit 0\n'''
    int rc = 0
    rc = sh(script: script, label: env.STAGE_NAME, returnStatus: true)
    if (rc != 0) {
        config['failBuildOnMissingReports'] = false;
    }
    return steps.publishValgrind(config)
}
