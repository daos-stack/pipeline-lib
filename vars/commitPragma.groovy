// vars/commitPragma.groovy

/**
 * commitPgragma.groovy
 *
 * commitPgragma variablecommitPgragma
 */

/**
 * Method to get a commit pragma value
 *
 *
 * @param config Map of parameters passed.
 *
 * config['pragma']     Pragma to get the value of
 * config['def_val']    Value to return if not found
 */
def call(Map config = [:]) {
    // convert the map for compat
    return commitPragma(config['pragma'], config['def_val'])
}

/**
 * @param name       Pragma to get the value of
 * @param def_val    Value to return if not found
 */
def call(String name, String def_val = null) {
    return commitPragmaTrusted(name, def_val)
}