// vars/jobStatusWrite.groovy

/**
 *
 * Write out the job status for a job
 *
 */

def call() {
    if (!env.DAOS_STACK_JOB_STATUS_DIR) {
        return
    }
    String job_name = env.JOB_NAME.replace('/', '_')
    job_name += '_' + env.BUILD_NUMBER
    String file_name = env.DAOS_STACK_JOB_STATUS_DIR + '/' + job_name

    String job_status_text = writeYaml(data: jobStatus(),
                                returnText: true)
    // Need to use shell script for creating files that are not
    // in the workspace.
    sh label: "Write jenkins_job_status ${file_name}",
       script: "echo \"${job_status_text}\" >> ${file_name}"
}
