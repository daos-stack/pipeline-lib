// vars/jobStatusUpdate.groovy

/**
 *
 * Update the status of a section of a job.
 *
 */

def call(String name='', String value='') {
    if (name == '') {
        name = env.STAGE_NAME
    }
    if (value == '') {
        value = currentBuild.currentResult
    }
    name = name.replace(' ', '_')
    name = name.replace('.', '_')
    jobStatus(name, value)
}
