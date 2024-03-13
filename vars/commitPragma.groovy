/* groovylint-disable ParameterName, VariableName */
// vars/commitPragma.groovy

/**
 * commitPgragma.groovy
 *
 * commitPgragma variablecommitPgragma
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

        if (pragmas[name.toLowerCase()]) {
            println("DEBUG - commitPragma(${name}) -> ${pragmas[name.toLowerCase()]}")
            return pragmas[name.toLowerCase()]
        } else if (def_val) {
            println("DEBUG - commitPragma(${name}) -> ${def_val}")
            return def_val
        }
        println("DEBUG - commitPragma(${name}) -> ''")
        return ''
    }
    println("DEBUG - commitPragma(${name}) -> commitPragmaTrusted()")
    return commitPragmaTrusted(name, def_val)
}
