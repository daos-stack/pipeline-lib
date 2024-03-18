// vars/jobStatusUpdate.groovy

/**
 * jobStatusUpdate.groovy
 *
 * Update the job status with a Map of test results.
 *
 * @param job_result Map of results for the entire job keyed for this stage
 *        stage String for the name of the stage
 *        result Map of results for the stage
 */
Void call(Map job_result, String stage, Map result) {
    echo "[jobStatus] Updating job result for stage ${stage} with result: ${result}"
    String stage_key = jobStatusKey(stage)
    if (!job_result.containsKey(stage_key)) {
        job_result."${stage_key}" = [:]
    }
    result.each { key, value ->
        job_result."${stage_key}"."${key}" = value
    }
}

/**
 * jobStatusUpdate.groovy
 *
 * Update the job status with a Map of test results.
 *
 * @param job_result Map of results for the entire job keyed for this stage
 *        stage String for the name of the stage
 *        result String of results for the stage
 */
Void call(Map job_result, String stage, String result) {
    Map result_map = ['result': result]
    jobStatusUpdate(job_result, stage, result_map)
}

/**
 * jobStatusUpdate.groovy
 *
 * Update the job status with a Map of test results.
 *
 * @param job_result Map of results for the entire job keyed for this stage
 *        stage String for the name of the stage
 */
Void call(Map job_result, String stage) {
    jobStatusUpdate(job_result, stage, currentBuild.currentResult)
}

/**
 * jobStatusUpdate.groovy
 *
 * Update the job status with a Map of test results.
 *
 * @param job_result Map of results for the entire job keyed for this stage
 */
Void call(Map job_result) {
    jobStatusUpdate(job_result, env.STAGE_NAME)
}
