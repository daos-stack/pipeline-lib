/* groovylint-disable MethodName, ParameterName, VariableName */
// vars/selfUnitTest.groovy

/**
 * selfUnitTest.groovy
 *
 * Runs self unit tests.
 */

void call() {
    // Save and restore env.pragmas after testing
    env_pragmas_original = env.pragmas
    try {
        _test_pragmas_env()
    } finally {
        env.pragmas = env_pragmas_original
    }
}
