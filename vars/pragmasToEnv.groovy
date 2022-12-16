/* groovylint-disable ParameterName */
// vars/pragmasToEnv.groovy

/**
 * pragmasToEnv.groovy
 *
 * pragmasToEnv variable
 */

String call(String commit_message) {
    Map pragmas = [:]
    // can't use eachLine() here: https://issues.jenkins.io/browse/JENKINS-46988/
    commit_message.split('\n').each { line ->
        String key, value
        try {
            (key, value) = line.split(':', 2)
            if (key.contains(' ')) {
                return
            }
            pragmas[key.toLowerCase()] = value
        /* groovylint-disable-next-line CatchArrayIndexOutOfBoundsException */
        } catch (ArrayIndexOutOfBoundsException ignored) {
            // ignore and move on to the next line
        }
    }

    return pragmas
}

/**
 * Method to put the commit pragmas into the environment
 */
Void call() {
    env.COMMIT_MESSAGE = sh(script: 'git show -s --format=%B',
                            returnStdout: true).trim()
    env.pragmas = pragmasToEnv(env.COMMIT_MESSAGE)

    return
}
