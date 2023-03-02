/* groovylint-disable DuplicateStringLiteral */
// vars/distroVersion.groovy

/**
 * run.groovy
 *
 * Map branch to distro versions
 */

/* groovylint-disable-next-line CompileStatic, UnusedVariable */
Map v = ['el8':      ['master':      '8.7',
                      'release/2.2': '8.6'],
         'leap15':   ['master':      '15.4',
                      'release/2.2': '15.4'],
         'ubuntu20': ['master':    '20.04']
]

String call() {
    return distroVersion(parseStageInfo()['target'])
}

String call(String distro) {
    String branch = 'master'
    if (env.BRANCH_NAME.endsWith('2.2')) {
        branch = 'release/2.2'
    }

    return distroVersion(distro, branch)
}

String call(String distro, String branch) {
    return v[distro][branch]
}
