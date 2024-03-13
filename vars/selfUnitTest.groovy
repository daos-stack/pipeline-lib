/* groovylint-disable ParameterName */
// vars/selfUnitTest.groovy

/**
 * selfUnitTest.groovy
 *
 * Runs self unit tests.
 */

String call() {
    commit_message = '''Skip-build: true
Skip-PR-comments: true

Required-githooks: true

Signed-off-by: Brian J. Murrell <brian.murrell@intel.com>'''
    Map expected_map = ["skip-build": " true", "skip-pr-comments": " true", "required-githooks": " true", "signed-off-by": " Brian J. Murrell <brian.murrell@intel.com>"]

    println("Test pragmasToMap")
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

    // println("Test pragmasToEnv")
    // TODO: there is inconsistent behavior between these casts:
    //       env.pragmas = pragmas
    //       return pragmas
    //       env.pragmas = pragmas as String
    // We should probably update the internals to always use "as String" so we have better control
}
