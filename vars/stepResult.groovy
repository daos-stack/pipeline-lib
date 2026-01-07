/* groovylint-disable DuplicateStringLiteral, VariableName */
// vars/stepResult.groovy

import com.intel.doGetHttpRequest

/**
 * stepResult.groovy
 *
 * stepResult pipeline step
 */

/* groovylint-disable-next-line MethodSize */
Void call(Map config= [:]) {
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
  Map param = [:]
  param['description'] = config['name']
  if (config['context'].contains('/')) {
    // Allow migration to common context value for scmNotify and
    // stepResult
    param['context'] = config['context']
  } else {
    param['context'] = config['context'] + '/' + config['name']
  }

  String flow_name = config.get('flow_name', env.STAGE_NAME)

  String log_url = null

  if (env.DAOS_JENKINS_NOTIFY_STATUS == null) {
    println 'Jenkins not configured to notify github of builds.'
    return
  }

  if (config['junit_files'] && config['result'] != 'FAILURE') {
    log_url = env.JOB_URL - ~/job\/[^\/]*\/$/ +
              "/view/change-requests/job/${env.BRANCH_NAME}/" +
              "${env.BUILD_ID}/testReport/"
  } else {
    def h = new doGetHttpRequest()
    resp = h.doGetHttpRequest(env.JOB_URL - ~/\/job\/[^\/]*\/$/ +
           '/view/change-requests/job/' +
           env.BRANCH_NAME.replaceAll('/', '%252F') +
           "/${env.BUILD_ID}/wfapi/describe")

    Map job = readJSON text: resp
    /* groovylint-disable-next-line Instanceof */
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

    stage = readJSON(text: resp)
    /* groovylint-disable-next-line Instanceof */
    assert stage instanceof Map

    def stageFlowNode = null
    for (s in stage['stageFlowNodes']) {
      if (s['name'] == flow_name) {
        stageFlowNode = s
        break
      }
    }
    if (stageFlowNode) {
      resp = h.doGetHttpRequest("${env.JENKINS_URL}" + stageFlowNode['_links']['log']['href'])
      log = readJSON(text: resp)
      /* groovylint-disable-next-line Instanceof */
      assert log instanceof Map

      log_url = "${env.JENKINS_URL}${log.consoleUrl}"
    } else {
      echo 'No step with label "' + flow_name +
           '" could be found run for this stage.'
      config['result'] = 'FAILURE'
      config['ignore_failure'] = false
    }
  }

    if (!config['ignore_failure']) {
        String cbResult = currentBuild.result
        String cbcResult = currentBuild.currentResult
        currentBuild.result = config.get('result')
        if (cbResult != currentBuild.result) {
            println 'stepResult changed result to ' + currentBuild.result + '.'
        }
        if (cbcResult != currentBuild.currentResult) {
            // Make this visible in the WEB UI
            String etext = 'stepResult changed currentResult to ' +
                           currentBuild.currentResult + '.'
            if (currentBuild.result == 'UNSTABLE') {
                unstable etext
            } else {
                println etext
            }
        }
    }

  if (config['result'] == 'ABORTED' ||
      config['result'] == 'UNSTABLE' ||
      config['result'] == 'FAILURE') {
    String comment_url = env.BUILD_URL + 'display/redirect'

    if (env.CHANGE_ID) {
      if (log_url) {
        comment_url = log_url
      }

      String msg = "Test stage ${config.name}" +
                ' completed with status ' +
                "${config.result}" +
                 '.  ' + comment_url

      if (commitPragma('Skip-PR-comments').toLowerCase() == 'true') {
          emailext subject: "${JOB_NAME} ${config.name} stage ${config.result}",
                   recipientProviders: [[$class: 'RequesterRecipientProvider'],
                                        [$class: 'DevelopersRecipientProvider']],
                   body: msg +
                         '\n\nIf you are receiving this e-mail, about a PR that is not yours, it is\n' +
                         'in error.\n\n' +
                         'Please accept my appologies and kindly forward this message to\n' +
                         'john.malmberg@hpe.com for investigation.\n\n' +
                         'Thank-you.\n\n\n' +
                         /* groovylint-disable-next-line GStringExpressionWithinString */
                         'BUILD_NUMBER=${BUILD_NUMBER}\n' +
                         /* groovylint-disable-next-line GStringExpressionWithinString */
                         'BUILD_TAG=${BUILD_TAG}\n' +
                         /* groovylint-disable-next-line GStringExpressionWithinString */
                         'BUILD_URL=${BUILD_URL}\n' +
                         /* groovylint-disable-next-line GStringExpressionWithinString */
                         'CHANGE_AUTHOR=${CHANGE_AUTHOR}\n' +
                         /* groovylint-disable-next-line GStringExpressionWithinString */
                         'CHANGE_BRANCH=${CHANGE_BRANCH}\n' +
                         /* groovylint-disable-next-line GStringExpressionWithinString */
                         'CHANGE_ID=${CHANGE_ID}\n' +
                         /* groovylint-disable-next-line GStringExpressionWithinString */
                         'CHANGE_TARGET=${CHANGE_TARGET}'
      } else {
          pullRequest.comment(msg)
      }
    }
      }

  String result = config['result']
  switch (config['result']) {
    case 'UNSTABLE':
      result = 'FAILURE'
      break
    case 'FAILURE':
      result = 'ERROR'
      break
  }
  if (log_url) {
    param['targetURL'] = log_url
  }
  param['status'] = result

  writeFile(file: stageStatusFilename(), text: config['result'])

  scmNotify param

  return
}
