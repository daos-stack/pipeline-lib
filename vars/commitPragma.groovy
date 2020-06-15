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

    return commitPragmaTrusted(config)
}