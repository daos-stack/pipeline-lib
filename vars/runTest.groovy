// vars/runTest.groovy
def call(Map config) {

    dir('install') {
        deleteDir()
    }
    config['stashes'].each {
        unstash it
    }

    script = '''if git show -s --format=%B | grep "^Skip-test: true"; then
                    exit 0
                fi\n''' + config['script']

    rc = sh(script: script, returnStatus: true)

    // All of this really should be done in the post section of the main
    // Jenkinsfile but it cannot due to
    // https://issues.jenkins-ci.org/browse/JENKINS-39203
    // Once that is fixed all of the below should be pushed up into the
    // Jenkinsfile post { stable/unstable/failure/etc. }
    if (rc != 0) {
        stepResult name: env.STAGE_NAME, context: "test", result: "FAILURE"
    } else if (rc == 0) {
        if (config['junit_files'] != null) {
            if (sh(script: "grep \"<error \" ${config.junit_files}",
                   returnStatus: true) == 0) {
                stepResult name: env.STAGE_NAME, context: "test", result: "FAILURE"
            } else if (sh(script: "grep \"<failure \" ${config.junit_files}",
                          returnStatus: true) == 0) {
                stepResult name: env.STAGE_NAME, context: "test", result: "UNSTABLE"
            } else {
                stepResult name: env.STAGE_NAME, context: "test", result: "SUCCESS"
            }
        }
    }
}
