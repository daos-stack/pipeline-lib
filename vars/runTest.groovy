// vars/runTest.groovy
def call(Map config) {

    dir('install') {
        deleteDir()
    }
    config['stashes'].each {
        unstash it
    }

    githubNotify credentialsId: 'daos-jenkins-commit-status',
                 description: env.STAGE_NAME,
                 context: "test" + "/" + env.STAGE_NAME,
                 status: "PENDING"

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
        status = "FAILURE"
    } else if (rc == 0) {
        if (config['junit_files'] != null) {
            if (sh(script: "grep \"<error \" ${config.junit_files}",
                   returnStatus: true) == 0) {
                status = "FAILURE"
            } else if (sh(script: "grep \"<failure \" ${config.junit_files}",
                          returnStatus: true) == 0) {
                status = "UNSTABLE"
            } else {
                status = "SUCCESS"
            }
        } else {
            status = "SUCCESS"
        }
    }
    stepResult name: env.STAGE_NAME, context: "test", result: status
}
