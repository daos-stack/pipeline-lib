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
        println("env.pragmas")
        Map pragmas = envToPragmas()
        pragmas.keySet().each { println it }

        if (pragmas[name.toLowerCase()]) {
            println("pragmas[${name.toLowerCase()}] -> ${pragmas[name.toLowerCase()]}")
            return pragmas[name.toLowerCase()]
        } else if (def_val) {
            println("${def_val} (default)")
            return def_val
        }
        println("(empty string)")
        return ''
    }
    return commitPragmaTrusted(name, def_val)
}
