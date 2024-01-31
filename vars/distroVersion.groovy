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
        } else if (env.CHANGE_TARGET) {
            branch = env.CHANGE_TARGET
        } else {
            // find the base branch
            branch = sh(label: 'Find base branch',
                         /* groovylint-disable-next-line GStringExpressionWithinString */
                         script: '''set -eux -o pipefail
                                    ORIGIN=origin
                                    mapfile -t all_bases < <(echo "master"; git branch -r |
                                      sed -ne "/^  $ORIGIN\\/release\\/[0-9]/s/^  $ORIGIN\\///p")
                                    TARGET="master"
                                    min_diff=-1
                                    for base in "${all_bases[@]}"; do
                                        git rev-parse --verify "$ORIGIN/$base" &> /dev/null || continue
                                        commits_ahead=$(git log --oneline "$ORIGIN/$base..HEAD" | wc -l)
                                        if [ "$min_diff" -eq -1 ] || [ "$min_diff" -gt "$commits_ahead" ]; then
                                            TARGET="$base"
                                            min_diff=$commits_ahead
                                        fi
                                    done
                                    echo "$TARGET"
                                    exit 0''',
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
assert(call('leap15', '2.4') == '15.5')
assert(call('leap15', 'master') == '15.5')
assert(call('el8', '2.4') == '8.8')
assert(call('el8', 'master') == '8.8')

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

assert(call('leap15') == '15.5')
assert(call('el8') == '8.8')
*/
