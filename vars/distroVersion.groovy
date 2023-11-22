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
    if ((env.BRANCH_NAME =~ branchTypeRE('release')    ||
         env.BRANCH_NAME =~ branchTypeRE('downstream') ||
         env.BRANCH_NAME =~ branchTypeRE('testing')) &&
        (env.BRANCH_NAME =~ '/\\d+\\.\\d+')) {
        branch = env.BRANCH_NAME.replaceFirst(/^.*(\d+\.\d+).*$/, '\$1')
    }

    return distroVersion(distro, branch)
}

String call(String distro, String branch) {
    return ['el8':      ['master': '8.8',
                         '2.4':    '8.8'],
            'el9':      ['master': '9.2'],
            'leap15':   ['master': '15.5',
                         '2.4':    '15.5'],
            'ubuntu20': ['master': '20.04']][distro][branch]
}

/* groovylint-disable-next-line CompileStatic */
assert(call('leap15', 'release/2.2') == '15.4')
assert(call('el8', 'release/2.2') == '8.6')
