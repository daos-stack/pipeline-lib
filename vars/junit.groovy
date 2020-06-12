// vars/junit.groovy

/**
 * junit.groovy
 *
 * Wrapper for junit step to allow for skipped tests.
 *
 */



def call(String testResults) {
    Map config = [:]
    config['testResults'] = testResults
    call(config)
}

def call(Map config = [:]) {
    def script = '''if [ "${NO_CI_TESTING}" == 'true' ]; then
                        exit 1
                    fi
                    if git show -s --format=%B | grep "^Skip-test: true"; then
                        exit 1
                    fi
                    exit 0\n'''
    int rc = 0
    rc = sh(script: script, label: env.STAGE_NAME + '_junit',
            returnStatus: true)
    if (rc != 0) {
        config['allowEmptyResults'] = true
    }
    steps.junit(config)
}
