/* groovylint-disable ParameterName */
// vars/envToPragmas.groovy

/**
 * envToPragmas.groovy
 *
 * envToPragmas variable
 */

Map call() {
    Map pragmas = [:]
    if (!env.pragmas) {
        println("DEBUG: !env.pragmas")
    }
    if (env.pragmas == null) {
        println("DEBUG: env.pragmas == null")

    }
    if (env.pragmas)
        pragmas = "${env.pragmas}"[1..-2].split(', ').collectEntries { entry ->
            String[] pair = entry.split('=', 2)
            [(pair.first()): pair.last()]
    }
    return pragmas
}
