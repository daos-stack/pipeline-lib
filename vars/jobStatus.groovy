// vars/jobStatus.groovy

/**
 *
 * Store status of the sections of a job.
 *
 */

def var_check() {
    if (! binding.hasVariable('jobStatusInternal')) {
        jobStatusInternal = [:]
    }
    println("##### ${env.STAGE_NAME}")
    jobStatusInternal.each { key, value ->
        println("##### ${key}: ${value}")
    }
}

def call() {
    var_check()
    return jobStatusInternal
}

def call(String name, value) {
    var_check()
    jobStatusInternal[name] = value
    return jobStatusInternal
}
