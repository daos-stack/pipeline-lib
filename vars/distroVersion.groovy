/* groovylint-disable DuplicateStringLiteral */
// vars/distroVersion.groovy

/**
 * run.groovy
 *
 * Map branch to distro versions
 */

String call() {
    return distroVersion(parseStageInfo()['target'])
}

String call(String distro) {
    String branch = 'master'
    if (env.BRANCH_NAME =~ branchTypeRE('release') ||
        env.BRANCH_NAME =~ branchTypeRE('testing')) {
        if (env.BRANCH_NAME =~ /\d+\.\d+/) {
            branch = env.BRANCH_NAME
        }
    } else {
        if (env.RELEASE_BRANCH) {
            branch = env.RELEASE_BRANCH
        } else {
            branch = releaseBranch()
        }
    }
    println("[distroVersion] distro=${distro}, branch=${branch}")
    
    return distroVersion(distro, branch.replaceFirst(/^.*[\/-](\d+\.\d+).*$/, '\$1'))
}

String call(String distro, String branch) {
    return ['el8':      ['master': '8.8',
                         '2.4':    '8.8',
                         '2.6':    '8.8'],
            'el9':      ['master': '9.7',
                         '2.6':    '9.4'],
            'leap15':   ['master': '15.6',
                         '2.4':    '15.6',
                         '2.6':    '15.6'],
            'ubuntu20': ['master': '20.04']][distro][branch]
}

/* groovylint-disable-next-line CompileStatic */
assert(call('leap15', '2.4') == '15.6')
assert(call('leap15', '2.6') == '15.6')
assert(call('leap15', 'master') == '15.6')
assert(call('el8', '2.4') == '8.8')
assert(call('el8', '2.6') == '8.8')
assert(call('el8', 'master') == '8.8')
assert(call('el9', 'master') == '9.7')
assert(call('el9', '2.6') == '9.4')

/* Uncomment to do further testing
env = [:]
String branchTypeRE(String foo) {
    return 'nomatch'
}

String distroVersion(String distro, String branch) {
    call(distro, branch)
}

String sh(Map args) {
    def sout = new StringBuilder(), serr = new StringBuilder()
    def cmd = ['/bin/bash', '-c', args['script']]
    def proc = cmd.execute()
    proc.consumeProcessOutput(sout, serr)
    proc.waitForOrKill(1000)
    //println "out> $sout\nerr> $serr"

    return sout
}

String releaseBranch() {
    return 'release/2.4'
}

assert(call('leap15') == '15.6')
assert(call('el8') == '8.8')
*/
