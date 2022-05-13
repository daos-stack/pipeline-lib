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
}

def key(String key) {
    var_check()
    jobStatusInternal[key] = env.STAGE_NAME
    println("##### ${env.STAGE_NAME}")
    jobStatusInternal.each { key, value ->
        println("##### ${key}: ${value}")
    }
}

def call(String key, value) {
    var_check()
    if (jobStatusInternal[key]) {
        jobStatusInternal[key] = value
    }
}

def call(String key) {
    var_check()
    if (! jobStatusInternal[key]) {
        jobStatusIntenal[key] = "Not Set"
    }
    return jobStatusInternal[key]
}
