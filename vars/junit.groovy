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
    // We really shouldn't even get here if $NO_CI_TESTING is true as the
    // when{} block for the stage should skip it entirely.  But we'll leave
    // this for historical purposes
    def script = 'if [ "' + env.NO_CI_TESTING + '''" == 'true' ]; then
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
    // don't trucate stdio/stdout in JUnit results
    // this actually shouldn't be necessary as stdio/stderr is supposed to
    // be preserved for failed results, however:
    // https://github.com/jenkinsci/junit-plugin/issues/219
    // https://issues.jenkins.io/browse/JENKINS-27931
    config['keepLongStdio'] = true
    steps.junit(config)
}
