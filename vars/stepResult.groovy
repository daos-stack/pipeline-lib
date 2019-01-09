// vars/stepResult.groovy

def call(Map config) {

    node {
        currentBuild.result = config.get('result')

        if (env.CHANGE_ID) {
           if (config['result'] == "ABORTED" ||
               config['result'] == "UNSTABLE" ||
               config['result'] == "FAILURE") {
                pullRequest.comment("Test stage ${config.name}" +
                                    " completed with status " +
                                    "${config.result}" +
                                    ".  " + env.BUILD_URL +
                                    "display/redirect")
            }

            def result = ''
            switch(config['result']) {
                case "UNSTABLE":
                    result = "FAILURE"
                    break
                case "FAILURE":
                    result = "ERROR"
                    break
            }
            githubNotify credentialsId: 'daos-jenkins-commit-status',
                         description: config['name'],
                         context: config['context'] + "/" + config['name'],
                         status: config['result']
        }
    }
}
