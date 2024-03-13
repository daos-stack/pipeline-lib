/* groovylint-disable ParameterName */
// vars/updatePragmas.groovy

/**
 * updatePragmas.groovy
 *
 * updatePragmas variable
 */

void call(String commit_message, boolean overwrite) {
    Map current_pragmas = envToPragmas()
    Map new_pragmas = pragmasToMap(commit_message)

    new_pragmas.each { entry ->
        if (overwrite || !current_pragmas.containsKey(entry.key)) {
            current_pragmas[entry.key] = entry.value
        }
    }

    // put the pragmas into the environment as a String
    // note this converts the Map to a string in the format "{foo= bar, bat= ball}"
    // instead of the expected format of "[foo:bar, bat:ball]"
    env.pragmas = pragmas
}
