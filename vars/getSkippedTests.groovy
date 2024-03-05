// vars/getSkippedTests.groovy

/**
 * run.groovy
 *
 * Get skipped tests from pragma
 */

List call(String branch) {
    // i.e. SkipList-master: myTestName:DAOS-1234 anotherTest:DAOS-3456
    List skiplist = cachedCommitPragma('SkipList', '').split(' ') +
                    cachedCommitPragma('SkipList-' + branch, '').split(' ')

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
    return 'myTestName:DAOS-1234 anotherTest:DAOS-3456'
}


// groovylint-disable-next-line CompileStatic
assert(call('master') == ['myTestName', 'anotherTest'])
*/
