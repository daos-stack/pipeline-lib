/* groovylint-disable DuplicateStringLiteral, GStringExpressionWithinString */
// vars/setupDownstreamTesting.groovy

String test_branch(String target, Map config = [:]) {
    if (config.validation) {
        return 'ryon-jensen-ci-' + JOB_NAME.replaceAll('/', '-') +
               '-' + env.BUILD_NUMBER + '-' + target.replaceAll('/', '-')
    }
    return 'ci-' + JOB_NAME.replaceAll('/', '-') + '-' + target.replaceAll('/', '-')
}

String gitAuthentication() {
    // The helper passes credentials over stdin/stdout, never through argv or a remote URL.
    return '''
        export GIT_CONFIG_COUNT=1
        export GIT_CONFIG_KEY_0=credential.helper
        export GIT_CONFIG_VALUE_0='!f() { printf "%s\\n" "username=$GH_USER" "password=$GH_PASS"; }; f'
        export GIT_TERMINAL_PROMPT=0
    '''
}

void cleanup(String project, String branch, Map config = [:]) {
    withCredentials(githubAccess()) {
        withEnv(["DOWNSTREAM_URL=https://github.com/${project}.git",
                 "DOWNSTREAM_BRANCH=${test_branch(branch, config)}"]) {
            sh label: 'Delete test branch',
               script: gitAuthentication() + '''
                   git push "$DOWNSTREAM_URL" --delete "$DOWNSTREAM_BRANCH"
               '''
        }
    }
}

/**
 * Set up a downstream test branch and return the legacy parameter defaults.
 * Optional validation configuration adds a build-unique branch, captured source
 * revisions, the actual test Jenkinsfile, and archived execution evidence.
 * Existing callers retain their branch names and returned parameter defaults.
 */
List call(String project, String branch, String commit_pragmas = '', Map config = [:]) {
    List knownParameters = ['CI_FI_el8_TEST',
                            'CI_MORE_FUNCTIONAL_PR_TESTS',
                            'CI_FUNCTIONAL_el9_TEST',
                            'CI_FUNCTIONAL_el8_TEST',
                            'CI_FUNCTIONAL_leap15_TEST',
                            'CI_medium_TEST',
                            'CI_large_TEST']
    String checkoutDir = project.split('/')[1] + '-' + branch.replaceAll('/', '-') +
                         (config.validation ? '-' + env.BUILD_NUMBER : '')
    config.checkoutDir = checkoutDir
    boolean selfTest = env.CHANGE_BRANCH && env.JOB_NAME.startsWith('daos-stack/pipeline-lib/')
    String libs = selfTest ? 'pipeline-lib@' + env.CHANGE_BRANCH : cachedCommitPragma('Test-libs')
    if (libs && !(libs ==~ /[A-Za-z0-9_,\/@.+:-]+/)) {
        error('Unsupported downstream test-library selector')
    }
    String message = (selfTest ? 'Update pipeline-lib branch to self' :
                                'Clear commit pragmas and update test libraries') +
                     '\n\n' + commit_pragmas
    withCredentials(githubAccess()) {
        withEnv(["DOWNSTREAM_URL=https://github.com/${project}.git",
                 "DOWNSTREAM_BRANCH=${test_branch(branch, config)}",
                 "DOWNSTREAM_SOURCE=origin/${cachedCommitPragma("Test-${branch}-branch", branch)}",
                 "DOWNSTREAM_DIR=${checkoutDir}",
                 "DOWNSTREAM_LIBS=${libs ?: ''}"]) {
            sh label: 'Create or update test branch',
               script: gitAuthentication() + '''
                   set -eu
                   if [ -d "$DOWNSTREAM_DIR/.git" ]; then
                       cd "$DOWNSTREAM_DIR"
                       git remote set-url origin "$DOWNSTREAM_URL"
                       git fetch origin --prune
                   else
                       git clone "$DOWNSTREAM_URL" "$DOWNSTREAM_DIR"
                       cd "$DOWNSTREAM_DIR"
                   fi
                   git checkout --detach origin/master
                   if git show-ref --verify --quiet "refs/heads/$DOWNSTREAM_BRANCH"; then
                       git branch -D "$DOWNSTREAM_BRANCH"
                   fi
                   git checkout -b "$DOWNSTREAM_BRANCH" "$DOWNSTREAM_SOURCE"
                   # Do not retain weekly triggers on synthetic branches.
                   if grep -q triggers Jenkinsfile; then
                       sed -i -e '/triggers/,/^$/d' Jenkinsfile
                   fi
                   if [ -n "$DOWNSTREAM_LIBS" ]; then
                       sed -i -e "/\\/\\/@Library/c\\
                           @Library(value='$DOWNSTREAM_LIBS') _" Jenkinsfile
                   fi
               '''
            if (config.validation) {
                config.sourceRevision = sh(script: "git -C '${checkoutDir}' rev-parse HEAD",
                                           returnStdout: true).trim()
                String path = checkoutDir + '/Jenkinsfile'
                writeFile file: path, text: packageCandidateValidation.instrument(readFile(path), config)
            }
            writeFile file: checkoutDir + '/.git/downstream-message', text: message
            sh label: 'Commit and push test branch',
               script: gitAuthentication() + '''
                   set -eu
                   cd "$DOWNSTREAM_DIR"
                   git commit ''' + (config.validation ? '-s ' : '') + '''--allow-empty -F .git/downstream-message -- Jenkinsfile
                   git show
                   git push -f origin "$DOWNSTREAM_BRANCH:$DOWNSTREAM_BRANCH"
                   sleep 10
               '''
            if (config.validation) {
                config.testRevision = sh(script: "git -C '${checkoutDir}' rev-parse HEAD",
                                         returnStdout: true).trim()
            }
        }
    }
    List result = []
    String jenkinsfilePath = checkoutDir + '/Jenkinsfile'
    if (fileExists(jenkinsfilePath)) {
        String content = readFile(jenkinsfilePath)
        if (config.validation) {
            // Capture this before checkout switches back to origin/master.
            config.jenkinsfile = content
        }
        knownParameters.each { param ->
            if (content.contains(param)) {
                result.add(booleanParam(name: param, value: !(param in ['CI_medium_TEST', 'CI_large_TEST'])))
            }
        }
    } else {
        echo "Warning: Jenkinsfile not found at ${jenkinsfilePath}"
    }
    withEnv(["DOWNSTREAM_DIR=${checkoutDir}", "DOWNSTREAM_BRANCH=${test_branch(branch, config)}"]) {
        sh label: 'Delete local test branch', script: '''
            set -eu
            cd "$DOWNSTREAM_DIR"
            git checkout --detach origin/master
            git branch -D "$DOWNSTREAM_BRANCH"
        '''
    }
    return result
}
