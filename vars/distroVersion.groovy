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
        println('It matches!!!!')
        if (env.BRANCH_NAME =~ /\d+\.\d+/) {
            branch = env.BRANCH_NAME.replaceFirst(/^.*(\d+\.\d+).*$/, '\$1')
            print('In distroVersion(), env.BRANCH_NAME == ' + env.BRANCH_NAME + ' and branch == ' + branch)
        } else {
            println('But it doesn\'t match /\\d+\\.\\d')
        }
    } else {
        if (env.BASE_BRANCH_NAME) {
            branch = env.BASE_BRANCH_NAME
            print('In distroVersion(), found BASE_BRANCH_NAME in the environment so branch == "' +
                    branch + '" and distro == "' + distro)
        } else {
            // find the base branch
            branch = sh(label: 'Find base branch',
                         script: '''set -eux -o pipefail
                                    env >&2
                                    max_commits=200
                                    base_branch_re='^  origin/(master|release/)'
                                    n=0
                                    while [ "$n" -lt "$max_commits" ]; do
                                        if git branch -r --contains HEAD~$n |
                                            grep -E "$base_branch_re" | sed -e 's/^ *[^\\/]*\\///'; then
                                            exit 0
                                        fi
                                        ((n++)) || true
                                    done
                                    echo "Could not find a base branch within $max_commits commits" >&2
                                    exit 1''',
                         returnStdout: true).trim().replaceFirst(/^.*(\d+\.\d+).*$/, '\$1')
            print('In distroVersion(), branch == "' + branch + '" and distro == "' + distro)
        }
    }

    println('" so returning "' + distroVersion(distro, branch) + '"')
    return distroVersion(distro, branch)
}

String call(String distro, String branch) {
    return ['el8':      ['master': '8.8',
                         '2.4':    '8.7'],
            'el9':      ['master': '9.2'],
            'leap15':   ['master': '15.5',
                         '2.4':    '15.4'],
            'ubuntu20': ['master': '20.04']][distro][branch]
}

/* groovylint-disable-next-line CompileStatic */
assert(call('leap15', 'release/2.2') == '15.4')
assert(call('el8', 'release/2.2') == '8.6')
