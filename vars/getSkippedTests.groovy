// vars/getSkippedTests.groovy

/**
 * run.groovy
 *
 * Get skipped tests from pragma
 */

List call(String branch) {
    // i.e. Skip-list: test_my_test:DAOS-1234 test_another_test:DAOS-3456
    //      Skip-list-master: test_only_master:DAOS-4567
    List skiplist = cachedCommitPragma('Skip-list', '').split(' ') +
                    cachedCommitPragma('Skip-list-' + branch, '').split(' ')

    echo 'Skip list: "' + skiplist + '" because "' + cachedCommitPragma('Skip-list', '') + '" and "' +
         cachedCommitPragma('Skip-list-' + branch, '') + '"'

    List skips = []
    skiplist.each { item ->
        if (!item.contains(':')) {
            error('Skip list item doesn\'t contain a :<ticket #>: ' + item)
        }
        skips += item.split(':')[0]
    }
    return skips.unique()
}

/* Uncomment to do further testing
Void error(String msg) {
    throw new RuntimeException(msg)
}

// groovylint-disable-next-line CompileStatic, NglParseError
String cachedCommitPragma(String p, String v) {
    return 'test_my_test:DAOS-1234 test_another_test:DAOS-3456'
}


// groovylint-disable-next-line CompileStatic
assert(call('master') == ['test_my_test', 'test_another_test'])
 */
