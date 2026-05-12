/* groovylint-disable ParameterName, VariableName */
// vars/commitPragma.groovy

/**
 * commitPragma.groovy
 *
 * commitPragma variablecommitPragma
 */

/**
 * Method to get a commit pragma value
 *
 * @param config Map of parameters passed.
 *
 * config['pragma']     Pragma to get the value of
 * config['def_val']    Value to return if not found
 */
String call(Map config = [:]) {
    // convert the map for compat
    return commitPragma(config['pragma'], config['def_val'])
}

/**
 * @param name       Pragma to get the value of
 * @param def_val    Value to return if not found
 */
String call(String name, String def_val = null) {
    if (env.pragmas) {
        Map pragmas = envToPragmas()

        String key = name.toLowerCase()
        def value = pragmas[key]

        if (key == 'test-tag') {
            if (value instanceof List) {
                return value.join(' ')
            }
            if (value != null) {
                return value.toString()
            }
            return def_val ?: ''
        }
        
        if (value instanceof List) {
            return value.join(' ')
        }
        if (value != null) {
            return value.toString()
        }
        return def_val ?: ''
    }

    if (name.toLowerCase() == 'test-tag') {
        return def_val ?: ''
    }

    def trusted = commitPragmaTrusted(name, def_val)
    return (trusted instanceof List) ? trusted.join(' ') : trusted
}
