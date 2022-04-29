/* groovylint-disable ParameterName */
// vars/paramsValue.groovy

/**
 * Method to get a parameter with a default value if it doesn't exist
 */

/* groovylint-disable-next-line MethodParameterTypeRequired, MethodReturnTypeRequired, NoDef */
def call(String parameter, def def_value) {

    if (params.containsKey(parameter) &&
        params[parameter] != "") {
        return params[parameter]
    }

    return def_value
}
