/* groovylint-disable DuplicateNumberLiteral, VariableName */
// vars/waitForDetachedTest.groovy

/**
 * waitForDetachedTest.groovy
 *
 * Wait for a test run that was launched detached from this agent to finish.
 *
 * The wait is a single long sh step, labelled with the stage name, that
 * streams the test output.  The tests themselves run on the test nodes, so if
 * the agent connection or the controller goes away the step can simply be
 * started again: it picks up where it left off and the tests carry on.
 */

/**
 * Method to wait for a detached test run
 *
 * @param config Map of parameters passed
 * @return The exit status of the test run
 *
 * config['script']         Wait script.  It is called with two arguments, the
 *                          check interval in seconds and the deadline as an
 *                          epoch time.  Exit status 0 means the run finished
 *                          and its exit status is in rc_file, 5 means no
 *                          detached run was launched, 6 means the deadline
 *                          passed.  Anything else is treated as a transient
 *                          failure and the script is started again.
 * config['kill_script']    Script to stop the run on abort or timeout.
 * config['rc_file']        File holding the exit status of the finished run.
 *                          Default 'ftest_detached/rc'.
 * config['interval']       Seconds between checks of the run.  Default 30.
 * config['retry_interval'] Seconds to wait before restarting a failed wait.
 *                          Default 60.
 * config['max_failures']   Consecutive failed waits before giving up.
 *                          Default 10.
 * config['timeout_hours']  Hours to wait before stopping the run.  Default 24.
 * config['label']          Label for the wait step.  Default env.STAGE_NAME.
 */
int call(Map config = [:]) {
    String wait_script = config['script']
    String kill_script = config.get('kill_script', '')
    String rc_file = config.get('rc_file', 'ftest_detached/rc')
    int interval = config.get('interval', 30)
    int retry_interval = config.get('retry_interval', 60)
    int max_failures = config.get('max_failures', 10)
    long timeout_ms = (config.get('timeout_hours', 24) as long) * 3600000L
    String label = config.get('label', env.STAGE_NAME)

    long deadline = System.currentTimeMillis() + timeout_ms
    String script = "${wait_script} ${interval} ${deadline.intdiv(1000)}"
    int failures = 0
    try {
        while (true) {
            int status = -1
            try {
                status = sh(script: script, label: label, returnStatus: true)
            } catch (InterruptedException e) {
                // FlowInterruptedException: the build was aborted
                throw e
            /* groovylint-disable-next-line CatchException */
            } catch (Exception e) {
                // e.g. the agent connection dropped during a restart
                println("Wait for detached test run failed: ${e}")
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
            if (status == 6 || System.currentTimeMillis() > deadline) {
                println("Detached test run exceeded ${config.get('timeout_hours', 24)} hours")
                stopRun(kill_script, label)
                return 124
            }
            failures++
            println("Wait for detached test run failed (${failures}/${max_failures})")
            if (failures >= max_failures) {
                println('Giving up on the detached test run')
                stopRun(kill_script, label)
                return 255
            }
            sleep(time: retry_interval, unit: 'SECONDS')
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
        sh(script: kill_script, label: label + ' (stop)', returnStatus: true)
    /* groovylint-disable-next-line CatchException */
    } catch (Exception e) {
        println("Unable to stop the detached test run: ${e}")
    }
}
