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
        println('Using env.pragmas:' + env.pragmas)
        Map pragmas = "${env.pragmas}"[1..-2].split(', ').collectEntries { entry ->
            String[] pair = entry.split('= ')
            [(pair.first()): pair.last()]
        }

        if (pragmas[name.toLowerCase()]) {
            return pragmas[name.toLowerCase()]
        } else if (def_val) {
            return def_val
        }
        return ''
    }
    println('Using commitPragmaTrusted():' + commitPragmaTrusted(name, def_val))
    return commitPragmaTrusted(name, def_val)
}
