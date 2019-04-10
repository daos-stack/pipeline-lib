// vars/stepResult.groovy

import com.intel.doGetHttpRequest
import groovy.json.JsonSlurperClassic

/**
 * stepResult.groovy
 *
 * stepResult pipeline step
 *
 */
def call(Map config= [:]) {
  /**
   * step reporting method.
   *
   * @param config Map of parameters passed
   * @return none
   *
   * config['result'] The result to post.
   * config['ignore_failure'] Whether a FAILURE result should post a failed step.
   * config['name'] The description to post in the GitHub commit status.
   * config['context'] The context to post in the GitHub commit status.
   * config['junit_files'] The names of any available junit files.
   */

    node {
        def log_url = null

        if (env.DAOS_JENKINS_NOTIFY_STATUS == null) {
          println "Jenkins not configured to notify github of builds."
          return
        }

        if (config['junit_files'] && config['result'] != 'FAILURE') {
            log_url = env.JOB_URL - ~/job\/[^\/]*\/$/ +
                      "/view/change-requests/job/${env.BRANCH_NAME}/" +
                      "${env.BUILD_ID}/testReport/(root)/"
        } else {
            def jsonSlurperClassic = new JsonSlurperClassic()

            def h = new com.intel.doGetHttpRequest()
            resp = h.doGetHttpRequest(env.JOB_URL - ~/job\/[^\/]*\/$/ +
                "/view/change-requests/job/${env.BRANCH_NAME}/" +
                "${env.BUILD_ID}/wfapi/describe");

            def job = jsonSlurperClassic.parseText(resp)
            assert job instanceof Map

            def stage
            for (s in job['stages']) {
                if (s['name'] == env.STAGE_NAME) {
                    stage = s
                    break
                }
            }

            resp = h.doGetHttpRequest("${env.JENKINS_URL}" + stage['_links']['self']['href'])

            stage = jsonSlurperClassic.parseText(resp)
            assert stage instanceof Map

            def stageFlowNode = null

            for (s in stage['stageFlowNodes']) {
                if (s['name'] == env.STAGE_NAME) {
                    stageFlowNode = s
                    break
                }
            }
            if (!stageFlowNode) {
                echo("No step named \"" + env.STAGE_NAME + "\" could be found for this stage.")
                config['result'] = "FAILURE"
                config['ignore_failure'] = false
            } else {
                resp = h.doGetHttpRequest("${env.JENKINS_URL}" + stageFlowNode['_links']['log']['href'])

                log = jsonSlurperClassic.parseText(resp)
                assert log instanceof Map

                log_url = "${env.JENKINS_URL}${log.consoleUrl}"
            }
            jsonSlurperClassic = null
        }

        if (!config['ignore_failure']) {
            currentBuild.result = config.get('result')
        }

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

            def result = config['result']
            switch(config['result']) {
                case "UNSTABLE":
                    result = "FAILURE"
                    break
                case "FAILURE":
                    result = "ERROR"
                    break
            }
            if (log_url) {
                githubNotify credentialsId: 'daos-jenkins-commit-status',
                             description: config['name'],
                             context: config['context'] + "/" + config['name'],
                             targetUrl: log_url,
                             status: result
            } else {
                githubNotify credentialsId: 'daos-jenkins-commit-status',
                             description: config['name'],
                             context: config['context'] + "/" + config['name'],
                             status: result
            }
        }
    }
}
