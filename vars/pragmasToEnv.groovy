/* groovylint-disable ParameterName */
// vars/pragmasToEnv.groovy

/**
 * pragmasToEnv.groovy
 *
 * pragmasToEnv variable
 */

Map call(String commit_message) {
    Map pragmas = [:]
    // can't use eachLine() here: https://issues.jenkins.io/browse/JENKINS-46988/
    commit_message.split('\n').each { line ->
        String key, value
        try {
            (key, value) = line.split(':', 2)
            if (key.contains(' ')) {
                // this returns from the .each closure, not the method
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
    String cmd = '''if [ -n "$GIT_CHECKOUT_DIR" ] && [ -d "$GIT_CHECKOUT_DIR" ]
                    then
                      cd "$GIT_CHECKOUT_DIR"
                    fi
                    git show -s --format=%B\n'''

    env.COMMIT_MESSAGE = sh(label: 'pragmasToEnv: lookup commit message',
                            script: cmd,
                            returnStdout: true).trim()
    env.pragmas = pragmasToEnv(env.COMMIT_MESSAGE)

    return
}
