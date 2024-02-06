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
            branch = env.BRANCH_NAME.replaceFirst(/^.*(\d+\.\d+).*$/, '\$1')
        }
    } else {
        if (env.BASE_BRANCH_NAME) {
            branch = env.BASE_BRANCH_NAME
        } else {
            // find the base branch
            branch = sh(label: 'Find base branch',
                         script: '''set -eux -o pipefail
                                    max_commits=300
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
        }
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
