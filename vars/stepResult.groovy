// vars/stepResult.groovy

import com.intel.doGetHttpRequest

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
   * config['flow_name'] Stage Flow name to match.  Default is env.STAGE_NAME.
   *                     For sh steps, the stage flow name is the label
   *                     assigned to the shell script.
   * config['junit_files'] The names of any available junit files.
   */
  Map param =[:]
  param['description'] = config['name']
  if (config['context'].contains('/')) {
    // Allow migration to common context value for scmNotify and
    // stepResult
    param['context'] = config['context']
  } else {
    param['context'] = config['context'] + "/" + config['name']
  }

  def flow_name = config.get('flow_name', env.STAGE_NAME)

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

    def h = new com.intel.doGetHttpRequest()
    resp = h.doGetHttpRequest(env.JOB_URL - ~/\/job\/[^\/]*\/$/ +
           "/view/change-requests/job/" +
           env.BRANCH_NAME.replaceAll('/', '%252F') +
           "/${env.BUILD_ID}/wfapi/describe");

    def job = readJSON text: resp
    assert job instanceof Map

    def stage
    for (s in job['stages']) {
      if (s['name'] == env.STAGE_NAME) {
        stage = s
        break
      }
    }
    resp = h.doGetHttpRequest("${env.JENKINS_URL}" +
           stage['_links']['self']['href'])

    stage = readJSON text: resp
    assert stage instanceof Map

    def stageFlowNode = null
    for (s in stage['stageFlowNodes']) {
      if (s['name'] == flow_name) {
        stageFlowNode = s
        break
      }
    }
    if (!stageFlowNode) {
      echo "No step with label \"" + flow_name +
           "\" could be found run for this stage."
      config['result'] = "FAILURE"
      config['ignore_failure'] = false
    } else {
      resp = h.doGetHttpRequest("${env.JENKINS_URL}" +
             stageFlowNode['_links']['log']['href'])
      log = readJSON text: resp
      assert log instanceof Map

      log_url = "${env.JENKINS_URL}${log.consoleUrl}"
    }
  }

  if (!config['ignore_failure']) {
    currentBuild.result = config.get('result')
  }

  if (config['result'] == "ABORTED" ||
      config['result'] == "UNSTABLE" ||
      config['result'] == "FAILURE") {
    def comment_url = env.BUILD_URL + "display/redirect"

    if (env.CHANGE_ID) {
      if (log_url) {
        comment_url = log_url
      }

      def msg = "Test stage ${config.name}" +
                " completed with status " +
                "${config.result}" +
                 ".  " + comment_url

      if (commitPragma(pragma: "Skip-PR-comments") == "true") {
          emailext subject: "${config.name} status: ${config.result}",
                   recipientProviders: [[$class: 'RequesterRecipientProvider'],
                                        [$class: 'DevelopersRecipientProvider']],
                   body: msg +
                         '\n\nIf you are receiving this e-mail, about a PR that is not yours, it is\n' +
                         'in error.\n\n' +
                         'Please accept my appologies and kindly forward this message to\n' +
                         'brian.murrell@intel.com for investigation.\n\n' +
                         'Thank-you.\n\n\n' +
                         'BUILD_NUMBER=${BUILD_NUMBER}\n' +
                         'BUILD_TAG=${BUILD_TAG}\n' +
                         'BUILD_URL=${BUILD_URL}\n' +
                         'CHANGE_AUTHOR=${CHANGE_AUTHOR}\n' +
                         'CHANGE_BRANCH=${CHANGE_BRANCH}\n' +
                         'CHANGE_ID=${CHANGE_ID}\n' +
                         'CHANGE_TARGET=${CHANGE_TARGET}'
      } else {
          pullRequest.comment(msg)
      }
    }
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
    param['targetURL'] = log_url
  }
  param['status'] = result

  scmNotify param
}
