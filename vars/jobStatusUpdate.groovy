// vars/jobStatusUpdate.groovy

/**
 * jobStatusUpdate.groovy
 *
 * Update the job status with a key and Map of test results.
 *
 * @param key String the santized stage name key for the job result
 *        result Map of test results from the stage
 * @return a Map of test results for this stage
 */
Map call(String key, Map result) {
    echo "[jobStatus] Updating result for ${key}"
    Map job_status = [key: [:]]
    job_status.key << result
    return job_status
}

/**
 * jobStatusUpdate.groovy
 *
 * Update the job status with a key and string of test results.
 *
 * @param key String the santized stage name key for the job result
 *        result String of the test result from the stage
 * @return a Map of test results for this stage
 */
Map call(String key, String result) {
    echo "[jobStatus] Updating result for ${key}"
    Map job_status = [key: ['result': value]]
    return job_status
}

/**
 * jobStatusUpdate.groovy
 *
 * Update the job status with a Map of test results.
 *
 * @param result Map of test results from the stage
 * @return a Map of test results for this stage
 */
Map call(Map result) {
    if result == null) {
        return job_status_update()
    }
    return job_status_update(jobStatusKey(env.STAGE_NAME), result)
}

/**
 * jobStatusUpdate.groovy
 *
 * Update the job status with the a key.
 *
 * @param key String the santized stage name key for the job result
 * @return a Map of test results for this stage
 */
Map call(String key) {
    return job_status_update(key, currentBuild.currentResult)
}

/**
 * jobStatusUpdate.groovy
 *
 * Update the job status.
 *
 * @return a Map of test results for this stage
 */
Map call() {
    return jobStatusUpdate(jobStatusKey(env.STAGE_NAME), currentBuild.currentResult)
}
