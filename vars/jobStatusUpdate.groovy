// vars/jobStatusUpdate.groovy

/**
 * jobStatusUpdate.groovy
 *
 * Update the job status.
 *
 * @param job_status Map containing the status of the job
 * @return a Map of 
 */
Void call(String key, Map result) {
    echo "[jobStatus] Updating result for ${key}"
    Map job_status = [key: [:]]
    if (result in Map) {
        job_status.key << result
    } else {
        job_status[key] = ['result': value]
    }
    return job_status
}

Void call(Map result) {
    if result == null) {
        return job_status_update()
    }
    return job_status_update(jobStatusKey(env.STAGE_NAME), result)
}

Void call(String key) {
    return job_status_update(key, currentBuild.currentResult)
}

Void call() {
    return jobStatusUpdate(jobStatusKey(env.STAGE_NAME), currentBuild.currentResult)
}
