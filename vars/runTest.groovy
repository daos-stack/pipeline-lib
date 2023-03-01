/* groovylint-disable DuplicateStringLiteral, MethodSize, VariableName
   groovylint-disable GStringExpressionWithinString */
// vars/runTest.groovy

Map call(Map config = [:]) {
  /**
   * runTest step method
   *
   * @param config Map of parameters passed
   * @return None
   *
   * config['junit_files'] Junit files to look for errors in
   * config['script'] The test code to run
   * config['stashes'] Stashes from the build to unstash
   * config['failure_artifacts'] Artifacts to link to when test fails, if any
   * config['ignore_failure'] Whether a FAILURE result should post a failed step
   * config['notify_result'] Flag to notify SCM for the resultstatus,
   *                         default true, Use false if the notification
   *                         will be in post processing.
   *
   * config['context'] Context name for SCM to identify the specific stage to
   *                   update status for.
   *                   Default is 'test/' + env.STAGE_NAME.
   *
   *  Important:
   *     The SCM status checking for passing may expect a specific name.
   *
   *     Matrix stages must override this setting to include matrix axes
   *     names to ensure a unique name is generated.
   *
   *     Or the default name has to be changed in a way that is compatible
   *     with a future Matrix implementation.
   *
   * config['flow_name'] Stage Flow name for logging.
   *                     Default is env.STAGE_NAME.
   *                     For sh steps, the stage flow name is the label
   *                     assigned to the shell script.
   *
   * config['description']  Description to report for SCM status.
   *                        Default env.STAGE_NAME.
   */

    // Todo
    // This routine is not "MATRIX" safe as it has an scmNOTIFY context
    // that only has env.STAGE_NAME.
    // The stepResult step is also using context differently than scmNotify
    // This has to be change here and in the
    // github expectations at the same time to also include any Matrix
    // environment variables.

    // Must use Date() in pipeline-lib
    // groovylint-disable-next-line NoJavaUtilDate
    Date startDate = new Date()
    String context = config.get('context', 'test/' + env.STAGE_NAME)
    String description = config.get('description', env.STAGE_NAME)
    String flow_name = config.get('flow_name', env.STAGE_NAME)

    dir('install') {
        deleteDir()
    }
    if (config['stashes']) {
        config['stashes'].each { name ->
            unstash name
        }
    }

    boolean ignore_failure = config.get('ignore_failure', false)
    boolean notify_result = config.get('notify_result', true)

    scmNotify description: description,
              context: context,
              status: 'PENDING'

    // We really shouldn't even get here if $NO_CI_TESTING is true as the
    // when{} block for the stage should skip it entirely.  But we'll leave
    // this for historical purposes
    String script = config['script']
    if (config['failure_artifacts']) {
        script += '''\nset +x\necho -n "Test artifacts can be found at: "
                     echo "${JOB_URL%/job/*}/view/change-requests/job/$BRANCH_NAME/$BUILD_ID/artifact/''' +
                          config['failure_artifacts'] + '"'
    }

    String cb_result = currentBuild.result
    int rc = 2
    try {
        sh(script: script, label: flow_name)
        rc = 0
    } catch (hudson.AbortException e) {
        rc = 1
    }

    // We need to pass the rc value to post step.
    // The currentBuild result is for all innerstages, not just this
    // stage, so can not be trusted for this.
    String result_stash = 'result_for_' + sanitizedStageName()
    writeFile(file: result_stash, text: "${rc}")
    stash name: result_stash,
          includes: result_stash

    if (cb_result != currentBuild.result) {
        println('Some other stage changed the currentBuild result to ' +
                "${currentBuild.result}.")
    }

    // All of this really should be done in the post section of the main
    // Jenkinsfile but it cannot due to
    // https://issues.jenkins-ci.org/browse/JENKINS-39203
    // Once that is fixed all of the below should be pushed up into the
    // Jenkinsfile post { stable/unstable/failure/etc. }

    String status = 'SUCCESS'
    if (rc != 0) {
        status = 'FAILURE'
    } else if (notify_result) {
        // Currently only used here for unit testing.
        status = checkJunitFiles(config)
    }

    if (notify_result) {
        stepResult name: description,
                   context: context,
                   flow_name: flow_name,
                   result: status,
                   junit_files: config['junit_files'],
                   ignore_failure: ignore_failure
    }

    if (status != 'SUCCESS') {
        if (ignore_failure) {
            catchError(stageResult: 'UNSTABLE',
                       buildResult: 'SUCCESS') {
                error(env.STAGE_NAME + ' failed: ' + rc)
            }
        } else {
            error(env.STAGE_NAME + ' failed: ' + rc)
        }
    }

    int runTime = durationSeconds(startDate)
    return ['result': status,
            'runtest_time': runTime]
}
