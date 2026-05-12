/* groovylint-disable ParameterName */
// vars/envToPragmas.groovy

/**
 * envToPragmas.groovy
 *
 * envToPragmas variable
 */

Map call() {
    if (!env.pragmas) {
        return [:]
    }

    if (env.pragmas instanceof Map) {
        return (Map) env.pragmas
    }

    Map pragmas = [:]
    pragmas = "${env.pragmas}"[1..-2].split(', ').collectEntries { entry ->
        String[] pair = entry.split('=', 2)
        [(pair.first()): pair.last()]
    }

    return pragmas
}
