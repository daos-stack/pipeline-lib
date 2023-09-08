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
    if (!job_result.containsKey("${stage_key}")) {
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

// /**
//  * jobStatusUpdate.groovy
//  *
//  * Update the job status with a key and Map of test results.
//  *
//  * @param key String the santized stage name key for the job result
//  *        result Map of test results from the stage
//  * @return a Map of test results for this stage
//  */
// Map call(String key, Map result) {
//     echo "[jobStatus] Updating result for ${key}"
//     Map job_status = ["${key}": [:]]
//     InvokerHelper.setProperties(job_status["${key}"], result)
//     return job_status
// }

// /**
//  * jobStatusUpdate.groovy
//  *
//  * Update the job status with a key and string of test results.
//  *
//  * @param key String the santized stage name key for the job result
//  *        result String of the test result from the stage
//  * @return a Map of test results for this stage
//  */
// Map call(String key, String result) {
//     echo "[jobStatus] Updating result for ${key}"
//     Map job_status = ["${key}": ['result': result]]
//     return job_status
// }

// /**
//  * jobStatusUpdate.groovy
//  *
//  * Update the job status with a Map of test results.
//  *
//  * @param result Map of test results from the stage
//  * @return a Map of test results for this stage
//  */
// Map call(Map result) {
//     if (result == null) {
//         return jobStatusUpdate()
//     }
//     return jobStatusUpdate(jobStatusKey(env.STAGE_NAME), result)
// }

// /**
//  * jobStatusUpdate.groovy
//  *
//  * Update the job status with the a String of test results.
//  *
//  * @param result String of the test result from the stage
//  * @return a Map of test results for this stage
//  */
// Map call(String result) {
//     return jobStatusUpdate(jobStatusKey(env.STAGE_NAME), result)
// }

// /**
//  * jobStatusUpdate.groovy
//  *
//  * Update the job status.
//  *
//  * @return a Map of test results for this stage
//  */
// Map call() {
//     return jobStatusUpdate(jobStatusKey(env.STAGE_NAME), currentBuild.currentResult)
// }
