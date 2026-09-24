/* groovylint-disable DuplicateNumberLiteral, VariableName */
// vars/waitForDetachedTest.groovy

/**
 * waitForDetachedTest.groovy
 *
 * Wait for a test run that was launched detached from this agent to finish.
 *
 * Each poll is a short sh step and the time between polls is a sleep step, so
 * a Jenkins controller restart does not need any process on the agent to
 * survive.  The pipeline resumes and carries on polling, and the tests keep
 * running on the test nodes the whole time.
 */

/**
 * Method to wait for a detached test run
 *
 * @param config Map of parameters passed
 * @return The exit status of the test run
 *
 * config['script']        Poll script.  Exit status 0 means the run finished
 *                         and its exit status is in rc_file, 3 means still
 *                         running, 5 means no detached run was launched.
 *                         Anything else is treated as a transient failure.
 * config['kill_script']   Script to stop the run on abort or timeout.
 * config['rc_file']       File holding the exit status of the finished run.
 *                         Default 'ftest_detached/rc'.
 * config['interval']      Seconds to sleep between polls.  Default 120.
 * config['max_failures']  Consecutive failed polls before giving up.
 *                         Default 30.
 * config['timeout_hours'] Hours to wait before stopping the run.  Default 24.
 * config['label']         Label for the poll steps.  Default env.STAGE_NAME.
 */
int call(Map config = [:]) {
    String poll_script = config['script']
    String kill_script = config.get('kill_script', '')
    String rc_file = config.get('rc_file', 'ftest_detached/rc')
    int interval = config.get('interval', 120)
    int max_failures = config.get('max_failures', 30)
    long timeout_ms = (config.get('timeout_hours', 24) as long) * 3600000L
    String label = config.get('label', env.STAGE_NAME) + ' (poll)'

    long deadline = System.currentTimeMillis() + timeout_ms
    int failures = 0
    try {
        while (true) {
            int status = -1
            try {
                status = sh(script: poll_script, label: label, returnStatus: true)
            } catch (InterruptedException e) {
                // FlowInterruptedException: the build was aborted
                throw e
            /* groovylint-disable-next-line CatchException */
            } catch (Exception e) {
                // e.g. the agent connection dropped mid-poll during a restart
                println("Poll of detached test run failed: ${e}")
            }

            if (status == 0) {
                if (!fileExists(rc_file)) {
                    println("Detached test run finished without writing ${rc_file}")
                    return 255
                }
                return Integer.parseInt(readFile(rc_file).trim())
            }
            if (status == 5) {
                // The tests ran inline and the launch step already passed.
                return 0
            }
            if (status == 3) {
                failures = 0
            } else {
                failures++
                println("Detached test poll failed (${failures}/${max_failures})")
                if (failures >= max_failures) {
                    println('Giving up on the detached test run')
                    stopRun(kill_script, label)
                    return 255
                }
            }
            if (System.currentTimeMillis() > deadline) {
                println("Detached test run exceeded ${config.get('timeout_hours', 24)} hours")
                stopRun(kill_script, label)
                return 124
            }
            sleep(time: interval, unit: 'SECONDS')
        }
    } catch (InterruptedException e) {
        stopRun(kill_script, label)
        throw e
    }
    return 255
}

void stopRun(String kill_script, String label) {
    if (!kill_script) {
        return
    }
    try {
        sh(script: kill_script, label: label.replace('(poll)', '(stop)'), returnStatus: true)
    /* groovylint-disable-next-line CatchException */
    } catch (Exception e) {
        println("Unable to stop the detached test run: ${e}")
    }
}
