// vars/jobStatusUpdate.groovy

/**
 *
 * Update the status of a section of a job.
 *
 */

def call(String name=env.STAGE_NAME, String value=currentBuild.currentResult) {
    name = name.replace(' ', '_')
    name = name.replace('.', '_')
    jobStatus(name, value)
}
