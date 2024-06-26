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

void _test_pragmas_env() {
    String commit_title = 'DAOS-XXXX test: short description about thing'
    String commit_desc = 'Long description about thing\nwith multiple lines'
    String commit_pragmas = '''Skip-build: true
Skip-PR-comments: true

My-pragma1:   val1
My-pragma2:  val2 val2

Required-githooks: true

Signed-off-by: Brian J. Murrell <brian.murrell@intel.com>'''
    String commit_message = commit_title + '\n\n' + commit_desc + '\n\n' + commit_pragmas

    Map expected_map = [
        'skip-build': 'true', 'skip-pr-comments': 'true',
        'my-pragma1': 'val1', 'my-pragma2': 'val2 val2',
        'required-githooks': 'true',
        'signed-off-by': 'Brian J. Murrell <brian.murrell@intel.com>']
    String expected_str = '{skip-build=true, skip-pr-comments=true, ' +
                          'my-pragma1=val1, my-pragma2=val2 val2, ' +
                          'required-githooks=true, signed-off-by=Brian J. Murrell <brian.murrell@intel.com>}'

    println('Test pragmasToMap')

    println('  with commit title and description only')
    result_map = pragmasToMap(commit_title + '\n\n' + commit_desc)
    println("    result_map   = ${result_map}")
    println("    expected_map = ${[:]}")
    assert(result_map == [:])

    println('  with full commit message')
    result_map = pragmasToMap(commit_message)
    println("    result_map   = ${result_map}")
    println("    expected_map = ${expected_map}")
    assert(result_map == expected_map)

    println('Test pragmasToEnv')
    result_str = pragmasToEnv(commit_message)
    println("    result_str   = ${result_str}")
    println("    env.pragmas  = ${env.pragmas}")
    println("    expected_str = ${expected_str}")
    assert(result_str == expected_str)
    assert(result_str == env.pragmas)

    println('Test envToPragmas')
    println('  with full env.pragmas')
    result_map = envToPragmas()
    println("    result_map   = ${result_map}")
    println("    expected_map = ${expected_map}")
    assert(result_map == expected_map)

    println('  with empty env.pragmas')
    env_pragmas_tmp = env.pragmas
    env.pragmas = ''
    result_map = envToPragmas()
    println("    result_map   = ${result_map}")
    println("    expected_map = ${[:]}")
    assert(result_map == [:])
    env.pragmas = env_pragmas_tmp

    println('Test updatePragmas')
    println('  with overwrite=true')
    commit_message = commit_title + '\n\n' + commit_desc + '\n\n' + 'Test-tag: foo bar'
    expected_map['test-tag'] = 'foo bar'
    updatePragmas(commit_message, true)
    result_map = envToPragmas()
    println("    result_map   = ${result_map}")
    println("    expected_map = ${expected_map}")
    assert(result_map == expected_map)

    expected_map['test-tag'] = 'foo2'
    updatePragmas('Test-tag: foo2', true)
    result_map = envToPragmas()
    println("    result_map   = ${result_map}")
    println("    expected_map = ${expected_map}")
    assert(result_map == expected_map)

    println('  with overwrite=false')
    updatePragmas('Test-tag: should_not_overwrite', false)
    result_map = envToPragmas()
    println("    result_map   = ${result_map}")
    println("    expected_map = ${expected_map}")
    assert(result_map == expected_map)

    println('Test getSkippedTests()')
    commit_message=commit_title + '\n\n' + commit_desc + '\n\n' +
                   'Skip-list: test_foo:DAOS-1234 test_bar:SRE-123'
    pragmasToEnv(commit_message)
    assert(getSkippedTests('master') == ['test_foo', 'test_bar'])
    withEnv(['STAGE_NAME=Functional Test']) {
        /* groovylint-disable-next-line UnnecessaryGetter */
        assert(getFunctionalTags() == 'pr,-test_foo,-test_bar,vm')
    }
}
