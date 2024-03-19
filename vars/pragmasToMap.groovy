/* groovylint-disable ParameterName */
// vars/pragmasToMap.groovy

/**
 * pragmasToMap.groovy
 *
 * pragmasToMap variable
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
            pragmas[key.toLowerCase()] = value.trim()
        /* groovylint-disable-next-line CatchArrayIndexOutOfBoundsException */
        } catch (ArrayIndexOutOfBoundsException ignored) {
            // ignore and move on to the next line
        }
    }

    return pragmas
}
