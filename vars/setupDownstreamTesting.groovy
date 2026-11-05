/* groovylint-disable DuplicateStringLiteral, GStringExpressionWithinString */
// vars/setupDownstreamTesting.groovy

String test_branch(String target) {
    return 'ci-' + JOB_NAME.replaceAll('/', '-') +
            '-' + target.replaceAll('/', '-')
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

void cleanup(String project, String branch) {
    withCredentials(githubAccess()) {
        withEnv(["DOWNSTREAM_URL=https://github.com/${project}.git",
                 "DOWNSTREAM_BRANCH=${test_branch(branch)}"]) {
            sh label: 'Delete test branch',
               script: gitAuthentication() + '''
                   git push "$DOWNSTREAM_URL" --delete "$DOWNSTREAM_BRANCH"
               '''
        }
    }
}

/** Delete only the unchanged synthetic commit prepared by this validation invocation. */
void cleanupValidation(String project, String branch, Map config) {
    Map created = config.createdRef
    if (!created) {
        return
    }
    if (created.project != project || created.branch != test_branch(branch) ||
        !(created.revision ==~ /[a-f0-9]{40}/)) {
        error('Invalid dependency-validation branch ownership')
    }
    withCredentials(githubAccess()) {
        withEnv(["DOWNSTREAM_URL=https://github.com/${project}.git",
                 "DOWNSTREAM_BRANCH=${created.branch}", "DOWNSTREAM_SHA=${created.revision}"]) {
            sh label: 'Delete owned validation branch',
               script: gitAuthentication() + '''
                   set -eu
                   ref="refs/heads/$DOWNSTREAM_BRANCH"
                   remote_ref=$(git ls-remote --heads "$DOWNSTREAM_URL" "$ref")
                   if [ -z "$remote_ref" ]; then
                       exit 0
                   fi
                   remote_sha=$(printf '%s\\n' "$remote_ref" | cut -f1)
                   if [ "$remote_sha" != "$DOWNSTREAM_SHA" ]; then
                       echo "Refusing to delete replaced validation branch $DOWNSTREAM_BRANCH" >&2
                       exit 1
                   fi
                   git push --force-with-lease="$ref:$DOWNSTREAM_SHA" "$DOWNSTREAM_URL" ":$ref"
               '''
        }
    }
}

/**
 * Set up a downstream test branch and return the legacy parameter defaults.
 * Validation configuration verifies explicit parameters and guards setup/test launches.
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
                 "DOWNSTREAM_BRANCH=${test_branch(branch)}",
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
                String path = checkoutDir + '/Jenkinsfile'
                String source = readFile(path)
                config.coverage = packageCandidateValidation.coverage(branch, config.distros, config.args, source)
                Map selected = config.coverage.parameters
                selected.TestTag = config.coverage.tags.split(' ').collect {
                    it + packageBuildingPipelineDAOSTest.skipped_tests_tags(branch)
                }.join(' ')
                selected.CI_PR_REPOS = config.launch.repositories
                selected.BuildPriority = getPriority().toString()
                if (config.coverage.prebuilt) {
                    selected.CI_RPM_TEST_VERSION = daosLatestVersion(branch, config.coverage.versionDistro)
                    if (!selected.CI_RPM_TEST_VERSION?.trim()) {
                        error("No prebuilt DAOS version for ${branch}")
                    }
                }
                packageCandidateValidation.validateParameters(branch, selected, source)
                config.launch.parameters = selected
                config.launch.id = "${config.launch.job}:${config.launch.build}".toString()
                if (source.readLines().findAll { it ==~ /job_status_internal\s*=.*/ }.size() != 1) {
                    error('Cannot guard downstream launch before its branch whitelist')
                }
                String encoded = groovy.json.JsonOutput.toJson(
                    groovy.json.JsonOutput.toJson(config.launch)).replace('$', '\\$')
                String guard = "if (!packageCandidateValidation.launch(${encoded})) { return }\n"
                writeFile file: path, text: source.replaceFirst(/(?m)^job_status_internal\s*=/) {
                    guard + it
                }
            }
            writeFile file: checkoutDir + '/.git/downstream-message', text: message
            sh label: 'Commit and push test branch',
               script: gitAuthentication() + '''
                   set -eu
                   cd "$DOWNSTREAM_DIR"
                   git commit ''' + (config.validation ? '-s ' : '') + '''--allow-empty -F .git/downstream-message -- Jenkinsfile
                   git show
                   remote_ref=$(git ls-remote --heads origin "refs/heads/$DOWNSTREAM_BRANCH")
                   if [ -n "$remote_ref" ]; then
                       echo "Refusing to overwrite existing downstream branch $DOWNSTREAM_BRANCH" >&2
                       exit 1
                   fi
               ''' + (config.validation ? '' : '''
                   git push origin "refs/heads/$DOWNSTREAM_BRANCH:refs/heads/$DOWNSTREAM_BRANCH"
               ''')
            if (config.validation) {
                String revision = sh(script: 'git -C "$DOWNSTREAM_DIR" rev-parse HEAD',
                                     returnStdout: true).trim()
                // Record before pushing so cancellation or later setup errors still permit cleanup.
                config.createdRef = [project: project, branch: test_branch(branch), revision: revision]
                sh label: 'Push validation branch', script: gitAuthentication() + '''
                    set -eu
                    cd "$DOWNSTREAM_DIR"
                    # An empty lease permits creation only, including races after the absence check.
                    git push --force-with-lease="refs/heads/$DOWNSTREAM_BRANCH:" origin \
                        "refs/heads/$DOWNSTREAM_BRANCH:refs/heads/$DOWNSTREAM_BRANCH"
                '''
            }
        }
    }
    List result = []
    String jenkinsfilePath = checkoutDir + '/Jenkinsfile'
    if (fileExists(jenkinsfilePath)) {
        String content = readFile(jenkinsfilePath)
        knownParameters.each { param ->
            if (content.contains(param)) {
                result.add(booleanParam(name: param, value: !(param in ['CI_medium_TEST', 'CI_large_TEST'])))
            }
        }
    } else {
        echo "Warning: Jenkinsfile not found at ${jenkinsfilePath}"
    }
    withEnv(["DOWNSTREAM_DIR=${checkoutDir}", "DOWNSTREAM_BRANCH=${test_branch(branch)}"]) {
        sh label: 'Delete local test branch', script: '''
            set -eu
            cd "$DOWNSTREAM_DIR"
            git checkout --detach origin/master
            git branch -D "$DOWNSTREAM_BRANCH"
        '''
    }
    return result
}

/** Poll only expected discovery/schema-not-ready responses; other HTTP failures propagate. */
void waitForJob(String job, Map selected = [:], String identity = '') {
    String url = env.JENKINS_URL + job.tokenize('/').collect {
        'job/' + URLEncoder.encode(it, 'UTF-8')
    }.join('/') + '/api/json?tree=' +
        URLEncoder.encode('property[parameterDefinitions[_class,name,defaultParameterValue[value]]]', 'UTF-8')
    timeout(time: 5, unit: 'MINUTES') {
        waitUntil(initialRecurrencePeriod: 5000) {
            def response = httpRequest(url: url, quiet: true, timeout: 30, validResponseCodes: '200,404')
            if (response.status == 404) {
                return false
            }
            if (!identity) {
                return true
            }
            Map jobInfo = new groovy.json.JsonSlurperClassic().parseText(response.content)
            List definitions = (jobInfo.property ?: []).collectMany { it.parameterDefinitions ?: [] }
            return definitions.any {
                it.name == 'DEPENDENCY_VALIDATION_ID' && it.defaultParameterValue?.value == identity
            } && selected.every { name, value ->
                definitions.any {
                    it.name == name &&
                        it._class == (value instanceof Boolean ?
                            'hudson.model.BooleanParameterDefinition' : 'hudson.model.StringParameterDefinition')
                }
            }
        }
    }
}
