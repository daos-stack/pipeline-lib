/* groovylint-disable ParameterName */
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

void _test_pragmas_env() {
    commit_message = '''Skip-build: true
Skip-PR-comments: true

Required-githooks: true

Signed-off-by: Brian J. Murrell <brian.murrell@intel.com>'''

    Map expected_map = [
        "skip-build": " true", "skip-pr-comments": " true","required-githooks": " true",
        "signed-off-by": " Brian J. Murrell <brian.murrell@intel.com>"]
    String expected_str = '{skip-build= true, skip-pr-comments= true, required-githooks= true,' +
                          ' signed-off-by= Brian J. Murrell <brian.murrell@intel.com>}'

    println("Test pragmasToMap")

    result_map = pragmasToMap(commit_message)
    println("  result_map   = ${result_map}")
    println("  expected_map = ${expected_map}")
    assert(result_map == expected_map)

    result_map = pragmasToMap("")
    expected_map = [:]
    println("  result_map   = ${result_map}")
    println("  expected_map = ${expected_map}")
    assert(result_map == expected_map)

    result_map = pragmasToMap("foo")
    println("  result_map   = ${result_map}")
    println("  expected_map = ${expected_map}")
    assert(result_map == expected_map)


    println("Test pragmasToEnv")

    result_str = pragmasToEnv(commit_message)
    println("  result_str   = ${result_str}")
    println("  env.pragmas  = ${env.pragmas}")
    println("  expected_str = ${expected_str}")
    assert(result_str == expected_str)
    assert(result_str == env.pragmas)


    println("Test envToPragmas")
    // TODO: there is inconsistent behavior between these casts:
    //       env.pragmas = pragmas
    //       return pragmas
    //       env.pragmas = pragmas as String
    // We should probably update the internals to always use "as String" so we have better control.
    // Related, envToPragmas will trim the value, so update the expected accordingly.
    // This should eventually be handled by trimming the value when we first read it.
    expected_map.each { entry ->
        expected_map[entry.value] = expected_map[entry.value].trim()
    }
    result_map = envToPragmas()
    println("  result_map   = ${result_map}")
    println("  expected_map = ${expected_map}")
    assert(result_map == expected_map)
}
