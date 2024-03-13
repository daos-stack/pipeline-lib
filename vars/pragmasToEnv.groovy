/* groovylint-disable ParameterName */
// vars/pragmasToEnv.groovy

/**
 * pragmasToEnv.groovy
 *
 * pragmasToEnv variable
 */

String call(String commit_message) {
    Map pragmas = pragmasToMap(commit_message)

    // put the pragmas into the environment as a String
    // note this converts the Map to a string in the format "{foo= bar, bat= ball}"
    // instead of the expected format of "[foo:bar, bat:ball]"
    env.pragmas = pragmas

    return env.pragmas
}

/**
 * Method to put the commit pragmas into the environment
 */
String call() {
    env.COMMIT_MESSAGE = sh(label: 'pragmasToEnv(): Get commit message',
                            script: '''if [ -n "$GIT_CHECKOUT_DIR" ] && [ -d "$GIT_CHECKOUT_DIR" ]; then
                                           cd "$GIT_CHECKOUT_DIR"
                                       fi
                                       git show -s --format=%B''',
                            returnStdout: true).trim()
    return pragmasToEnv(env.COMMIT_MESSAGE)
}
