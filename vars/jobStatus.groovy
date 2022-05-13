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

def call(String key, value) {
    var_check()
    if (jobStatusInternal[key]) {
        jobStatusInternal[key] = value
    }
}

def call(String key) {
    var_check()
    if (! jobStatusInternal[key]) {
        println("#### setting")
        jobStatusInternal[key] = "Not Set"
    }
    return jobStatusInternal[key]
}
