/* groovylint-disable DuplicateStringLiteral */
// vars/releaseBranch.groovy

/**
 * releaseBranch.groovy
 *
 * Return the release branch the PR will be landed to
 */

String call() {
    /* groovylint-disable-next-line GStringExpressionWithinString */
    String script = '''set -eux -o pipefail
                       mapfile -t all_bases < <(echo "master"; git branch -r |
                         sed -ne "/^  origin\\/release\\/[0-9]/s/^  origin\\///p")
                       target="master"
                       min_diff=-1
                       for base in "${all_bases[@]}"; do
                           git rev-parse --verify "origin/$base" &> /dev/null || continue
                           commits_ahead=$(git log --oneline "origin/$base..HEAD" | wc -l)
                           if [ "$min_diff" -eq -1 ] || [ "$min_diff" -gt "$commits_ahead" ]; then
                               target="$base"
                               min_diff=$commits_ahead
                           fi
                       done
                       echo "$target"
                       exit 0'''
    if (fileExists('utils/rpms/packaging/get_release_branch')) {
        script = 'utils/rpms/packaging/get_release_branch'
    } else if (fileExists('packaging/get_release_branch')) {
        script = 'packaging/get_release_branch'
    }
    // find the release branch
    return sh(label: 'Find release branch',
              script: script,
              returnStdout: true).trim()
}

/* Uncomment for some UT
Boolean fileExists(String path) {
    return false
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

println(call())
*/
