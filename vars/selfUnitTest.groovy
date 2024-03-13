/* groovylint-disable ParameterName */
// vars/pragmasToEnv.groovy

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
    Map expected_map = ["skip-build": "true", "skip-pr-comments": "true", "required-githooks": "true", "signed-off-by": "Brian J. Murrell <brian.murrell@intel.com>"]

    println("Test pragmasToMap")
    result = pragmasToMap(commit_message)
    assert(result == expected_map), result + ' != ' expected_map

    result = pragmasToMap("")
    assert(result == [:]), result + ' != ' [:]

    result = pragmasToMap("foo")
    assert(result == [:]), result + ' != ' [:]

    // println("Test pragmasToEnv")
    // TODO: there is inconsistent behavior between these casts:
    //       env.pragmas = pragmas
    //       return pragmas
    //       env.pragmas = pragmas as String
    // We should probably update the internals to always to "as String" so we have better control
}
