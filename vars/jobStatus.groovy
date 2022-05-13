// vars/jobStatus.groovy

/**
 *
 * Store status of the sections of a job.
 *
 */

def var_check() {
    if (! binding.hasVariable('jobStatusInternal')) {
        println("##### failed")
        return false
    }
    println("##### ${env.STAGE_NAME}")
    jobStatusInternal.each { key, value ->
        println("##### ${key}: ${value}")
    }
}

def call() {
    if (! var_check()) {
        return false
    }
    return jobStatusInternal
}

def call(String name, value) {
    if (!var_check()) {
        return [:]
    }
    jobStatusInternal[name] = value
    return jobStatusInternal
}
