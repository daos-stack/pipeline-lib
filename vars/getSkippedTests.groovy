// vars/getSkippedTests.groovy

/**
 * run.groovy
 *
 * Get skipped tests from pragma
 */

List call(String branch = null) {
    // i.e. Skip-list: test_my_test:DAOS-1234 test_another_test:DAOS-3456
    //      Skip-list-master: test_only_master:DAOS-4567
    List skiplist = cachedCommitPragma('Skip-list', '').split() +
                    (branch ? cachedCommitPragma('Skip-list-' + branch, '').split() : [])

    List skips = []
    skiplist.eachWithIndex { item, i ->
        if (!item.contains(':')) {
            error('Skip list (' + skiplist + ') item #' + (i + 1) + ' (' + item +
                  ') doesn\'t contain a :<ticket #>')
        }
        skips += item.split(':')[0]
    }
    return skips.unique()
}

/* Uncomment to do further testing
Void error(String msg) {
    // groovylint-disable-next-line ThrowRuntimeException
    throw new RuntimeException(msg)
}

Void echo(String msg) {
    println(msg)
    return
}

// groovylint-disable-next-line CompileStatic, NglParseError
String cachedCommitPragma(String p, String v) {
    if (p == 'Skip-list') {
        return 'test_my_test:DAOS-1234 test_another_test:DAOS-3456 test'
    }
    return v

}

// groovylint-disable-next-line CompileStatic
assert(call('master') == ['test_my_test', 'test_another_test'])
*/
