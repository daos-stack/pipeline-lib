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
                // this returns from the .each closure, not the method
                return
            }
            pragmas[key.toLowerCase()] = value
        /* groovylint-disable-next-line CatchArrayIndexOutOfBoundsException */
        } catch (ArrayIndexOutOfBoundsException ignored) {
            // ignore and move on to the next line
        }
    }

    // put the pragms into the environment
    env.pragmas = pragmas

    // note this converts the Map to a string in the format "{foo= bar, bat= ball}"
    // instead of the expected format of "[foo:bar, bat:ball]"
    return pragmas
}

/**
 * Method to put the commit pragmas into the environment
 */
Void call() {
    env.COMMIT_MESSAGE = sh(label: 'pragmasToEnv(): Get commit message',
                            script: '''if [ -n "$GIT_CHECKOUT_DIR" ] && [ -d "$GIT_CHECKOUT_DIR" ]; then
                                           cd "$GIT_CHECKOUT_DIR"
                                       fi
                                       git show -s --format=%B''',
                            returnStdout: true).trim()
    env.pragmas = pragmasToEnv(env.COMMIT_MESSAGE)

    return env.pragmas
}

// Unit Testing
/* groovylint-disable-next-line CompileStatic */
env = [:]
assert(call('''Debug for env.pragmas

Skip-build: true
Skip-PR-comments: true

Required-githooks: true

Signed-off-by: Brian J. Murrell <brian.murrell@intel.com>''') ==
'{skip-build= true, skip-pr-comments= true, required-githooks= true,' +
' signed-off-by= Brian J. Murrell <brian.murrell@intel.com>}')
