/* groovylint-disable ParameterName, VariableName */
// vars/jobStatusWrite.groovy

/**
 * jobStatusWrite.groovy
 *
 * Write the job status.
 *
 * @param job_status Map containing the status of the job
 */
Void call(Map job_status) {
    String jobName = env.JOB_NAME.replace('/', '_') + '_' + env.BUILD_NUMBER
    echo "[jobStatus] Writing result for ${jobName}"
    if (!env.DAOS_STACK_JOB_STATUS_DIR) {
        echo '[jobStatus] The DAOS_STACK_JOB_STATUS_DIR is undefined'
        return
    }
    String dirName = env.DAOS_STACK_JOB_STATUS_DIR + '/' + jobName + '/'
    String job_status_text = writeYaml data: job_status, returnText: true

    // Need to use shell script for creating files that are not in the workspace.
    sh(script: """mkdir -p ${dirName}
                  echo "${job_status_text}" >> ${dirName}jenkins_result""",
       label: "Write jenkins_job_status ${dirName}jenkins_result")
    return
}
