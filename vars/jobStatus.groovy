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
    println("jobStatus key, vaue called")
    var_check()
    jobStatusInternal[key] = value

    jobStatusInternal.each {key1, value1 ->
        println("#### ${key1}: ${value1}")
    }
}

def call(String key) {
    var_check()
    if (! jobStatusInternal[key]) {
        println("#### setting default")
        jobStatusInternal[key] = "Not Set"
    } else {
        println("#### reading existing")
    }
    return jobStatusInternal[key]
}
