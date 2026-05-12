/* groovylint-disable ParameterName */
// vars/pragmasToMap.groovy

/**
 * pragmasToMap.groovy
 *
 * pragmasToMap variable
 */

Map call(String commit_message) {
    if (!commit_message) {
        error('Valid commit message required')
    }

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
            String k = key.toLowerCase()
            String v = value.trim()
            
            // Special handling: allow multiple Test-tag pragmas
            if (k == 'test-tag') {
                def existing = pragmas[k]
                if (existing == null) {
                    pragmas[k] = [v]
                } else if (existing instanceof List) {
                    pragmas[k] << v
                } else {
                    pragmas[k] = [existing, v]
                }
            } else {
                // default behavior for all other pragmas
                pragmas[k] = v
            }
        /* groovylint-disable-next-line CatchArrayIndexOutOfBoundsException */
        } catch (ArrayIndexOutOfBoundsException ignored) {
            // ignore and move on to the next line
        }
    }

    return pragmas
}
