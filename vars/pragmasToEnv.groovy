// vars/pragmasToEnv.groovy

/**
 * pragmasToEnv.groovy
 *
 * pragmasToEnv variable
 */


/**
 * Method to put the commit pragmas into the environment
 */
Void call() {
    env.COMMIT_MESSAGE = sh(script: 'git show -s --format=%B',
                            returnStdout: true).trim()
    Map pragmas = [:]
    // can't use eachLine() here: https://issues.jenkins.io/browse/JENKINS-46988/
    env.COMMIT_MESSAGE.split('\n').each { line ->
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
    env.pragmas = pragmas

    return
}
