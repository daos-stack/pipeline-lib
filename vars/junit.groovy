// vars/junit.groovy

/**
 * run.groovy
 *
 * Wrapper for junit step to allow for skipped tests.
 *
 */



def call(String testResults = '') {
    println "Entering junit(String) override."
        Map config = [:];
    if (testResults != '') {
        config['testResults'] = testResults
    } else {
       error("Need to provide testResults: path.")
    }
}

def call(config map [:])
    println "Entering junit(Map) override."
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
