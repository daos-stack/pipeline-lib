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
        _test_pragmasToMap()
        _test_pragmasToEnv()
    } finally {
        env.pragmas = env_pragmas_original
    }
}

void _test_pragmasToMap() {
    println("Test pragmasToMap")

    commit_message = '''Skip-build: true
Skip-PR-comments: true

Required-githooks: true

Signed-off-by: Brian J. Murrell <brian.murrell@intel.com>'''

    Map expected_map = ["skip-build": " true", "skip-pr-comments": " true", "required-githooks": " true", "signed-off-by": " Brian J. Murrell <brian.murrell@intel.com>"]

    result = pragmasToMap(commit_message)
    println("  result   = ${result}")
    println("  expected = ${expected_map}")
    assert(result == expected_map)

    result = pragmasToMap("")
    expected_map = [:]
    println("  result   = ${result}")
    println("  expected = ${expected_map}")
    assert(result == expected_map)

    result = pragmasToMap("foo")
    println("  result   = ${result}")
    println("  expected = ${expected_map}")
    assert(result == expected_map)
}

void _test_pragmasToEnv() {
    println("Test pragmasToEnv")

    commit_message = '''Skip-build: true
Skip-PR-comments: true

Required-githooks: true

Signed-off-by: Brian J. Murrell <brian.murrell@intel.com>'''

    String expected_str = '{skip-build= true, skip-pr-comments= true, required-githooks= true,' +
                          ' signed-off-by= Brian J. Murrell <brian.murrell@intel.com>}'

    result = pragmasToEnv(commit_message)
    println("  result      = ${result}")
    println("  env.pragmas = ${env.pragmas}")
    println("  expected    = ${expected_str}")
    assert(result == expected_str)
    assert(result == env.pragmas)


    // println("Test pragmasToEnv")
    // TODO: there is inconsistent behavior between these casts:
    //       env.pragmas = pragmas
    //       return pragmas
    //       env.pragmas = pragmas as String
    // We should probably update the internals to always use "as String" so we have better control
}
