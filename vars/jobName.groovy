// vars/jobName.groovy

/**
 * jobName.groovy
 *
 * jobName variable
 */

/**
 * Method to return the name of the running job
 */

String call() {
    // JOB_NAME=daos-stack/mpich/PR-65
    String[] jobNameParts = env.JOB_NAME.split('/')
    return jobNameParts.length < 2 ? env.JOB_NAME : jobNameParts[jobNameParts.length - 2]
}
