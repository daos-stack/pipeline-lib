/* groovylint-disable ParameterName */
// vars/envToPragmas.groovy

/**
 * envToPragmas.groovy
 *
 * envToPragmas variable
 */

Map call() {
    Map pragmas = [:]
    if (env.pragmas)
        pragmas = "${env.pragmas}"[1..-2].split(', ').collectEntries { entry ->
            String[] pair = entry.split('=', 2)
            [(pair.first()): pair.last()]
    }
    return pragmas
}


env = [:]
env.pragmas = ""
env.pragmas = null

println(call())