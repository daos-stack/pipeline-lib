/*
 * Copyright 2026 Hewlett Packard Enterprise Development LP
 *
 * SPDX-License-Identifier: BSD-2-Clause-Patent
 */

/**
 * Candidate validation/publication policy used only by packageBuildingPipelineDAOSTest.
 * SRE-4139: candidates are immutable, all branch results are required, and approval
 * cannot replace missing coverage, a successful build, or artifact integrity.
 */
List branches() {
    return ['master', 'release/2.6', 'release/2.8']
}

Map repositories() {
    return [
        centos7: [format: 'yum', tech: 'el-7'],
        el8: [format: 'yum', tech: 'el-8'],
        el9: [format: 'yum', tech: 'el-9'],
        leap15: [format: 'yum', tech: 'leap-15'],
        'ubuntu20.04': [format: 'apt', tech: 'ubuntu-20.04'],
        ubuntu_rolling: [format: 'apt', tech: 'ubuntu-rolling']
    ]
}

/** Discover actual declared parameters, including modern bashName stage selectors. */
Map declarations(String jenkinsfile) {
    Map result = [:]
    String block = jenkinsfile.split(/(?m)^\s*parameters\s*\{/, 2).last()
    block = block.split(/(?m)^\s*stages\s*\{/, 2).first()
    block.readLines().findAll { line ->
        line ==~ /\s*(?:booleanParam|string|choice|text|password)\(name:.*$/
    }.each { line ->
        def match = line =~ /(booleanParam|string|choice|text|password)\(name:\s*(?:bashName\('([^']+)'\)|'([^']+)')/
        if (match.find()) {
            String name = match.group(2) ? match.group(2).replaceAll('[^a-zA-Z0-9]', '_') : match.group(3)
            result[name] = match.group(1)
        }
    }
    return result
}

/** Configure requested controls explicitly; target declarations only validate this policy. */
Map configuredParameters(String branch, boolean rebuild, boolean units, List compatible) {
    Map modern = [
        CI_IGNORE_SKIP_COMMIT_PRAGMAS: false,
        Cancel_Previous_Builds: false, Pre_build: rebuild,
        Python_Bandit_check: rebuild, Build: rebuild,
        Build_on_EL_9: rebuild && 'el9' in compatible,
        Build_on_Leap_15: rebuild && 'leap15' in compatible,
        Unit_Tests: units, Unit_Test: units, Unit_Test_bdev: units, NLT: units,
        Unit_Test_with_memcheck: units, Unit_Test_bdev_with_memcheck: units,
        Test: true, Test_Hardware: false,
        Functional_on_EL_9: 'el9' in compatible,
        Functional_on_Leap_15: 'leap15' in compatible,
        Functional_on_Ubuntu_20_04: 'ubuntu20.04' in compatible,
        Functional_on_SLES_15: false, Functional_on_EL_9_with_Valgrind: false,
        Fault_injection_testing: false,
        Test_RPMs_on_EL_9: rebuild && 'el9' in compatible,
        Test_RPMs_on_Leap_15: rebuild && 'leap15' in compatible,
        Functional_Hardware_Medium: false,
        Functional_Hardware_Medium_MD_on_SSD: false,
        Functional_Hardware_Medium_VMD: false,
        Functional_Hardware_Large: false,
        Functional_Hardware_Large_MD_on_SSD: false
    ]
    Map policies = [
        master: modern + [
            Functional_Hardware_Medium_Provider: false,
            Functional_Hardware_Medium_Provider_MD_on_SSD: false,
            Functional_Cluster_Box_Medium_MD_on_SSD: false,
            Functional_Cluster_Box_Medium_Provider_MD_on_SSD: false
        ],
        'release/2.8': modern + [
            Functional_Hardware_Medium_Verbs_Provider: false,
            Functional_Hardware_Medium_Verbs_Provider_MD_on_SSD: false,
            Functional_Hardware_Medium_UCX_Provider: false
        ],
        'release/2.6': [
            CI_CANCEL_PREV_BUILD_SKIP: true, CI_UNIT_TEST: units,
            CI_NLT_TEST: units, CI_UNIT_TEST_MEMCHECK: units,
            CI_FI_el8_TEST: false, CI_FUNCTIONAL_TEST_SKIP: false,
            CI_FUNCTIONAL_HARDWARE_TEST_SKIP: true,
            CI_MORE_FUNCTIONAL_PR_TESTS: true,
            CI_FUNCTIONAL_el8_VALGRIND_TEST: false,
            CI_TEST_EL8_RPMs: rebuild && 'el8' in compatible,
            CI_TEST_LEAP15_RPMs: rebuild && 'leap15' in compatible,
            CI_el8_NOBUILD: !rebuild || !('el8' in compatible),
            CI_el9_NOBUILD: !rebuild || !('el9' in compatible),
            CI_leap15_NOBUILD: !rebuild || !('leap15' in compatible),
            CI_RPM_el8_NOBUILD: !rebuild || !('el8' in compatible),
            CI_RPM_el9_NOBUILD: !rebuild || !('el9' in compatible),
            CI_RPM_leap15_NOBUILD: !rebuild || !('leap15' in compatible),
            CI_DEB_Ubuntu20_NOBUILD: !rebuild || !('ubuntu20.04' in compatible),
            CI_FUNCTIONAL_el8_TEST: 'el8' in compatible,
            CI_FUNCTIONAL_el9_TEST: 'el9' in compatible,
            CI_FUNCTIONAL_leap15_TEST: 'leap15' in compatible,
            CI_FUNCTIONAL_ubuntu20_TEST: 'ubuntu20.04' in compatible,
            CI_medium_TEST: false,
            CI_medium_md_on_ssd_TEST: false,
            CI_medium_vmd_TEST: false,
            CI_medium_verbs_provider_TEST: false,
            CI_medium_verbs_provider_md_on_ssd_TEST: false,
            CI_medium_ucx_provider_TEST: false,
            CI_large_TEST: false,
            CI_large_md_on_ssd_TEST: false
        ]
    ]
    if (!policies.containsKey(branch)) {
        error("No configured DAOS validation parameters for ${branch}")
    }
    return [
        CI_BUILD_PACKAGES_ONLY: false, CI_ALLOW_UNSTABLE_TEST: true,
        CI_RPM_TEST_VERSION: '', CI_PROVISIONING_POOL: 'default',
        CI_BUILD_DESCRIPTION: 'Dependency candidate validation (VM only)'
    ] + policies[branch]
}

/** Reject schema drift, including newly introduced hardware controls, before launch. */
void validateParameters(String branch, Map parameters, String jenkinsfile) {
    Map declared = declarations(jenkinsfile)
    parameters.each { name, value ->
        String expected = value instanceof Boolean ? 'booleanParam' : 'string'
        if (!declared.containsKey(name)) {
            error("DAOS ${branch} no longer declares configured parameter ${name}")
        }
        if (declared[name] != expected) {
            error("DAOS ${branch} parameter ${name} is ${declared[name]}, expected ${expected}")
        }
    }
    declared.each { name, type ->
        if (name == 'Test_Hardware' || name == 'CI_FUNCTIONAL_HARDWARE_TEST_SKIP' ||
            name.startsWith('Functional_Hardware_') || name.startsWith('Functional_Cluster_Box_') ||
            name.startsWith('CI_medium') || name.startsWith('CI_large') ||
            name.startsWith('CI_FUNCTIONAL_HARDWARE_') || name.startsWith('CI_CLUSTER_BOX_')) {
            boolean disabled = name == 'CI_FUNCTIONAL_HARDWARE_TEST_SKIP'
            if (!parameters.containsKey(name) || parameters[name] != disabled) {
                error("DAOS ${branch} hardware/cluster-box control ${name} has no configured disable policy")
            }
        }
    }
}

/** Select only supported coverage with compatible candidate packages. */
Map coverage(String branch, List distros, Map args, String jenkinsfile) {
    if (!(branch in branches())) {
        error("No configured DAOS validation parameters for ${branch}")
    }
    boolean modern = branch != 'release/2.6'
    boolean rebuild = !args.get('skip-build', true)
    String primary = modern ? 'el9' : 'el8'
    List compatible = (modern ? ['el9', 'leap15', 'ubuntu20.04'] :
                                ['el8', 'el9', 'leap15', 'ubuntu20.04']).findAll { it in distros }
    if (!compatible) {
        error("No compatible candidate distro for DAOS ${branch}: ${distros}")
    }
    if (rebuild && 'ubuntu20.04' in compatible) {
        error("DAOS ${branch} has no enabled Ubuntu rebuild stage; prebuilt DAOS is required for Ubuntu candidates")
    }
    boolean units = rebuild && primary in compatible
    Map selected = configuredParameters(branch, rebuild, units, compatible)
    List required = []
    List built = []
    List functional = []
    if (modern) {
        ['el9': 'EL 9', leap15: 'Leap 15'].each { distro, label ->
            if (rebuild && distro in compatible) {
                built << "Build on ${label}".toString()
                required << "Test RPMs on ${label}".toString()
            }
        }
        if (units) {
            required += ['Unit Test', 'Unit Test bdev', 'NLT',
                         'Unit Test with memcheck', 'Unit Test bdev with memcheck']
        }
        ['el9': 'EL 9', leap15: 'Leap 15', 'ubuntu20.04': 'Ubuntu 20.04'].each { distro, label ->
            if (distro in compatible) {
                functional << "Functional on ${label}".toString()
            }
        }
    } else {
        ['el8': 'EL 8', 'el9': 'EL 9', leap15: 'Leap 15'].each { distro, label ->
            if (rebuild && distro in compatible) {
                built << "Build on ${label}".toString()
                built << "Build RPM on ${label}".toString()
            }
        }
        ['el8': 'EL 8', 'el9': 'EL 9', leap15: 'Leap 15', ubuntu20: 'Ubuntu 20.04'].each { distro, label ->
            boolean enabled = (distro == 'ubuntu20' ? 'ubuntu20.04' : distro) in compatible
            if (enabled) {
                functional << "Functional on ${label}".toString()
            }
        }
        if (units) {
            required += ['Unit Test on EL 8', 'Unit Test bdev on EL 8', 'NLT on EL 8',
                         'Unit Test with memcheck on EL 8', 'Unit Test bdev with memcheck on EL 8']
        }
        if (rebuild && 'el8' in compatible) {
            required << 'Test RPMs on EL 8'
        }
        if (rebuild && 'leap15' in compatible) {
            required << 'Test RPMs on Leap 15'
        }
    }
    validateParameters(branch, selected, jenkinsfile)
    return [parameters: selected, tags: ('load_mpi test_core_files ' + args.get('test-tag', '')).trim(),
            versionDistro: primary in compatible ? primary : compatible.first(),
            required: required + functional, built: built, functional: functional,
            compatibleDistros: compatible, prebuilt: !rebuild]
}

/** Add restart-durable source/stage evidence to the temporary downstream branch only. */
String instrument(String jenkinsfile, Map config = [:]) {
    String anchor = 'jobStatusWrite(job_status_internal)'
    if (jenkinsfile.count(anchor) != 1) {
        error('Cannot instrument downstream Jenkinsfile: expected one final jobStatusWrite')
    }
    if (jenkinsfile.count('    environment {') < 1) {
        error('Cannot instrument downstream Jenkinsfile: no pipeline environment')
    }
    String result = jenkinsfile.replaceFirst(/    environment \{/, '''    environment {
        GOTOOLCHAIN = 'local'
''')
    if (config.goIdentities) {
        String check = compilerCheck(config.goIdentities)
        result = result.replaceAll(/(?m)^(\s*)job_step_update\(\s*\n(\s*)sconsBuild\(/) { match ->
            match[1] + check + '\n' + match[0]
        }
        if (!result.contains('Verify candidate Go compiler')) {
            error('Cannot locate DAOS build steps for candidate compiler verification')
        }
        result = result.replace("buildRpmPost condition: 'success', rpmlint: true", """
            sh(label: 'Reject downloaded Go toolchains', script: '''set -eu
                logs=\$(find /var/lib/mock -path '*/result/build.log' -type f)
                test -n "\$logs"
                set +e
                grep -H -E 'go: downloading go[0-9]|golang.org/toolchain' \$logs
                result=\$?
                set -e
                test "\$result" -eq 1
            ''')
            buildRpmPost condition: 'success', rpmlint: true""")
    }
    result = result.replace(anchor, anchor + '''
            writeFile(file: 'dependency-validation-evidence.json',
                text: groovy.json.JsonOutput.toJson([
                    sourceRevision: sh(script: 'git rev-parse HEAD^', returnStdout: true).trim(),
                    testRevision: sh(script: 'git rev-parse HEAD', returnStdout: true).trim(),
                    repositories: params.CI_PR_REPOS,
                    stages: job_status_internal]))
            archiveArtifacts(artifacts: 'dependency-validation-evidence.json', fingerprint: true)
''')
    return config.validation ? protectLaunch(result, config.launch) : result
}

/** SCM indexing/webhooks may schedule a new branch with hardware-enabled defaults. */
String protectLaunch(String jenkinsfile, Map launch) {
    if (!launch?.job || !(launch.build ==~ /\d+/) || !launch.repositories ||
        jenkinsfile.readLines().findAll { line -> line.startsWith('pipeline {') }.size() != 1) {
        error('Cannot protect synthetic branch launch: missing upstream identity or pipeline anchor')
    }
    String expected = groovy.json.JsonOutput.toJson(launch)
    String guard = '''def dependencyValidationLaunch = new groovy.json.JsonSlurperClassic().parseText(''' +
        groovy.json.JsonOutput.toJson(expected).replace('$', '\\$') + ''')
if (!currentBuild.getBuildCauses('hudson.model.Cause$UpstreamCause').any { cause ->
        cause.upstreamProject == dependencyValidationLaunch.job &&
        cause.upstreamBuild.toString() == dependencyValidationLaunch.build
    } || params.CI_PR_REPOS != dependencyValidationLaunch.repositories) {
    echo 'Synthetic dependency validation branch: ignoring non-matching or automatic launch'
    return
}
'''
    return jenkinsfile.replaceFirst(/(?m)^pipeline \{/) { guard + 'pipeline {' }
}

/** Verify the installed compiler's RPM identity and version inside each DAOS build container. */
String compilerCheck(List identities) {
    if (!identities || identities.any { !(it ==~ /[A-Za-z0-9+_.~|:-]+/) }) {
        error('Missing or invalid candidate Go RPM identities')
    }
    return """sh(label: 'Verify candidate Go compiler', script: '''set -eu
        {
        test "\$GOTOOLCHAIN" = local
        compiler=\$(readlink -f "\$(command -v go)")
        identity=\$(rpm -qf --qf '%{NAME}-%{VERSION}-%{RELEASE}.%{ARCH}|%{SIGMD5}' "\$compiler")
        printf '%s\\\\n' '${identities.join('\n')}' | grep -F -x -- "\$identity"
        version=\$(rpm -qf --qf '%{VERSION}' "\$compiler")
        test "\$(go env GOVERSION)" = "go\$version"
        go version
        printf '%s\\\\n' "\$identity"
        sha256sum "\$compiler"
        }''' + ' > candidate-compiler-' + sanitizedStageName() + '.txt')
        archiveArtifacts(artifacts: 'candidate-compiler-*.txt', fingerprint: true)"""
}

/** Freeze complete package trees and checksums while still on the successful build agent. */
Map archiveCandidate(String distro) {
    if (!repositories().containsKey(distro)) {
        error("Unsupported candidate distro: ${distro}")
    }
    String packages = repositories()[distro].format == 'yum' ? '*.rpm' : '*.deb'
    String metadata = repositories()[distro].format == 'yum' ? 'repodata/repomd.xml' : 'Packages.gz'
    String source = repositories()[distro].format == 'yum' ? '*.src.rpm' : '*.dsc'
    sh label: "Freeze ${distro} candidates", script: """set -eu
        test -n "\$(find artifacts/${distro} -type f -name '${packages}' -print -quit)"
        test -n "\$(find artifacts/${distro} -type f -name '${source}' -print -quit)"
        test -s artifacts/${distro}/${metadata}
        test -z "\$(find artifacts/${distro} ! -type f ! -type d -print -quit)"
        mkdir -p candidates
        find artifacts/${distro} -type f -exec sha256sum {} + | LC_ALL=C sort > candidates/${distro}.files.sha256
        tar -cf candidates/${distro}.tar artifacts/${distro}
        sha256sum candidates/${distro}.tar > candidates/${distro}.tar.sha256
    """
    Map candidate = [distro: distro,
                     sourceRevision: sh(script: 'git rev-parse HEAD', returnStdout: true).trim(),
                     sha256: readFile("candidates/${distro}.tar.sha256").tokenize().first(),
                     files: readFile("candidates/${distro}.files.sha256")]
    if (repositories()[distro].format == 'apt') {
        candidate.distribution = sh(script: '. /etc/os-release; printf "%s" "$VERSION_CODENAME"',
                                     returnStdout: true).trim()
        if (!(candidate.distribution ==~ /[a-z][a-z0-9-]*/)) {
            error("Missing or invalid DEB distribution for ${distro}")
        }
    } else {
        candidate.rpmIdentities = sh(script: "find artifacts/${distro} -type f -name '*.rpm' " +
            "! -name '*.src.rpm' -exec rpm -qp " +
            "--qf '%{NAME}-%{VERSION}-%{RELEASE}.%{ARCH}|%{SIGMD5}\\n' {} +",
            returnStdout: true).trim().readLines()
    }
    archiveArtifacts artifacts: "candidates/${distro}.*", fingerprint: true
    return candidate
}

boolean publishBranch(Map environment, String branch) {
    return !environment.CHANGE_ID && environment.BRANCH_NAME == branch &&
           !environment.BRANCH_NAME.startsWith('PR-')
}

boolean validApproval(Map approval) {
    return approval?.APPROVER?.trim() && approval?.JUSTIFICATION?.trim()
}

String validationResult(List results) {
    if (results.any { it.result == 'ABORTED' }) {
        return 'ABORTED'
    }
    if (results.any { it.result != 'SUCCESS' && it.result != 'UNSTABLE' }) {
        return 'FAILURE'
    }
    return results.any { it.result == 'UNSTABLE' } ? 'UNSTABLE' : 'SUCCESS'
}

String decision(List results) {
    if (results.size() != branches().size() ||
        results.collect { it.branch }.toSet() != branches().toSet() ||
        results.any { !it.evidenceValid || !(it.result in ['SUCCESS', 'FAILURE', 'UNSTABLE']) }) {
        return 'block'
    }
    return results.every { it.result == 'SUCCESS' } ? 'publish' : 'approve'
}

/** Compare required stage execution and non-skipped JUnit coverage, not just the parent result. */
boolean stageMatches(String actual, String expected) {
    String key = expected.replaceAll('[ .]', '_')
    return actual == key || (expected ==~ /.*\d/ &&
           actual.startsWith(key + '_') && actual.substring(key.length() + 1) ==~ /\d+(_.*)?/)
}

boolean evidenceValid(Map result, Map evidence, Map tests, String repositories) {
    if (evidence.sourceRevision != result.sourceRevision ||
        evidence.testRevision != result.testRevision ||
        evidence.repositories != repositories || !evidence.stages) {
        return false
    }
    Map stages = evidence.stages
    List allowed = ['SUCCESS', 'FAILURE', 'UNSTABLE']
    boolean valid = (result.coverage.built + result.coverage.required).every { name ->
        List matches = stages.findAll { stage, value ->
            stageMatches(stage, name)
        }.values() as List
        matches && matches.every { entry ->
            entry.result in (name in result.coverage.built ? ['SUCCESS'] : allowed)
        }
    }
    if (stages.any { name, value ->
        (name.startsWith('Functional_Hardware_') || name.startsWith('Functional_Cluster_Box_')) &&
        value.result in allowed
    }) {
        return false
    }
    return valid && result.coverage.required.every { name ->
        (tests.suites ?: []).any { suite ->
            suite.enclosingBlockNames?.any { it == name || it.startsWith(name + '.') } &&
            suite.cases?.any { it.status in ['PASSED', 'FIXED', 'FAILED', 'REGRESSION'] }
        }
    }
}

void writeReport(Map state) {
    writeFile file: 'dependency-validation.json',
              text: groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(state))
    archiveArtifacts artifacts: 'dependency-validation.json', fingerprint: true
}

void report(Map state) {
    node('lightweight') {
        writeReport(state)
    }
}

/** Await an explicitly authorized post-test decision without occupying a node. */
void approve(Map state) {
    String submitters = env.DAOS_DEPENDENCY_APPROVERS?.trim()
    if (!submitters || !(submitters ==~ /[A-Za-z0-9_.@-]+(,[A-Za-z0-9_.@-]+)*/)) {
        error('Manual publication disabled: administrator must configure DAOS_DEPENDENCY_APPROVERS')
    }
    state.approval = [status: 'PENDING']
    report(state)
    try {
        Map answer = timeout(time: 24, unit: 'HOURS') {
            input message: 'Publish these exact dependency candidates despite completed test failures?\n' +
                           state.results.collect { "${it.branch}: ${it.result} ${it.url}" }.join('\n'),
                  ok: 'Approve publication', submitter: submitters,
                  submitterParameter: 'APPROVER',
                  parameters: [string(name: 'JUSTIFICATION', defaultValue: '',
                                      description: 'Required: reason for overriding the test failures')]
        }
        if (!validApproval(answer)) {
            state.approval = [status: 'INVALID', actor: answer.APPROVER]
            error('Publication approval requires an identified approver and nonblank justification')
        }
        state.approval = [status: 'APPROVED', actor: answer.APPROVER,
                          justification: answer.JUSTIFICATION.trim(),
                          candidateBuild: state.buildUrl,
                          candidates: state.candidates.collectEntries { distro, value -> [(distro): value.sha256] }]
    } catch (Exception exception) {
        if (state.approval.status == 'PENDING') {
            state.approval = [status: 'DENIED_OR_INTERRUPTED']
        }
        throw exception
    } finally {
        report(state)
    }
}

/** Verify every archived tree before any repository is touched. Caller supplies a fresh directory. */
void restore(Map state) {
    copyArtifacts projectName: env.JOB_NAME, selector: specific(env.BUILD_NUMBER),
                  filter: 'candidates/**'
    state.candidates.each { distro, candidate ->
        if (!(candidate.sha256 ==~ /[0-9a-f]{64}/)) {
            error("Invalid candidate digest: ${distro}")
        }
        sh label: "Verify archived ${distro} candidates", script: """set -eu
            printf '%s  %s\\n' '${candidate.sha256}' 'candidates/${distro}.tar' | sha256sum -c -
            tar -xf candidates/${distro}.tar
        """
        // The archive's manifest is not the authority: compare with the original frozen manifest.
        writeFile file: "candidates/${distro}.files.sha256", text: candidate.files
        sh label: "Verify ${distro} package bytes",
           script: "sha256sum -c candidates/${distro}.files.sha256"
    }
}

/** Publish all supported distro paths with explicit per-repository partial-failure evidence. */
void publish(Map state, String product, String publishBranch) {
    if (!this.publishBranch([BRANCH_NAME: env.BRANCH_NAME, CHANGE_ID: env.CHANGE_ID], publishBranch)) {
        error('Stable publication is forbidden for this branch')
    }
    if (!env.REPOSITORY_URL || !env.ARTIFACTORY_URL) {
        error('Stable publication requires REPOSITORY_URL and ARTIFACTORY_URL')
    }
    lock(resource: "dependency-publication-${product}") {
        node('lightweight') {
            dir("candidate-publication-${env.BUILD_NUMBER}") {
                deleteDir()
                restore(state)
                boolean failed = false
                state.candidates.each { distro, candidate ->
                    state.publication[distro] = [status: 'PUBLISHING', sha256: candidate.sha256]
                    writeReport(state)
                    try {
                        Map route = repositories()[distro] + [
                            product: product, maturity: 'stable',
                            repo_dir: "artifacts/${distro}/", publish_branch: publishBranch,
                            download_dir: "verified-upload-${distro}", required: true]
                        if (candidate.distribution) {
                            route.distribution = candidate.distribution
                        }
                        publishToRepository(route)
                        state.publication[distro].status = 'SUCCESS'
                    } catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException interrupted) {
                        state.publication[distro].status = 'INTERRUPTED_POSSIBLY_PARTIAL'
                        throw interrupted
                    } catch (Exception exception) {
                        state.publication[distro] += [status: 'FAILED_POSSIBLY_PARTIAL',
                                                       error: exception.toString()]
                        failed = true
                    } finally {
                        writeReport(state)
                    }
                }
                if (failed) {
                    error('Publication partially failed; inspect dependency-validation.json. No rollback was attempted.')
                }
            }
        }
    }
}

/** Run all required branches, then enforce the publication policy for every shared caller. */
void call(Map args, List distros, Map state, String product, String publishBranch) {
    state.results = []
    state.publication = [:]
    state.candidateBuildResult = currentBuild.currentResult
    state.buildUrl = env.BUILD_URL
    state.upstream = [job: env.JOB_NAME, branch: env.BRANCH_NAME, build: env.BUILD_NUMBER]
    try {
        if (currentBuild.currentResult != 'SUCCESS' || !distros ||
            distros.toSet().size() != distros.size() ||
            state.candidates.keySet() != distros.toSet() ||
            state.candidates.any { distro, value -> value.sourceRevision != state.sourceRevision }) {
            error('Candidate build is incomplete, skipped, unsuccessful, or has inconsistent source revisions')
        }
        currentBuild.keepLog = true
        String project = env.JOB_NAME.tokenize('/')[-2]
        String pinned = "${project}@${env.BRANCH_NAME}:${env.BUILD_NUMBER}"
        String extra = cachedCommitPragma('PR-repos') ?: ''
        if (extra.tokenize().any { it.tokenize('@').first() == project }) {
            error("PR-repos must not override the pinned ${project} candidate")
        }
        String repos = (pinned + ' ' + extra).trim()
        branches().each { branch ->
            Map result = [branch: branch, result: 'NOT_BUILT', evidenceValid: false]
            state.results << result
            Map setup = [validation: true,
                         launch: [job: env.JOB_NAME, build: env.BUILD_NUMBER, repositories: repos]]
            if (product == 'go-build-toolchain') {
                setup.goIdentities = state.candidates.values().collectMany { it.rpmIdentities ?: [] }.unique()
                if (!setup.goIdentities) {
                    error('Go toolchain validation requires candidate RPM identities')
                }
            }
            String job = ''
            try {
                node('lightweight') {
                    String pragmas = '''Skip-test-hardware: true
Skip-func-test-hw: true
Skip-func-hw-test: true
Skip-fault-injection: true
Skip-fault-injection-test: true
Skip-unit-tests: ''' + (args.get('skip-build', true) ||
                        !((branch == 'release/2.6' ? 'el8' : 'el9') in distros)) + '\n' +
                        (('ubuntu20.04' in distros) ? 'Skip-func-test-ubuntu20: false\n' : '')
                    setupDownstreamTesting('daos-stack/daos', branch, pragmas, setup)
                    result.coverage = coverage(branch, distros, args, setup.jenkinsfile)
                    result.sourceRevision = setup.sourceRevision
                    result.testRevision = setup.testRevision
                    Map params = result.coverage.parameters
                    params.TestTag = result.coverage.tags.split(' ').collect { tag ->
                        tag + packageBuildingPipelineDAOSTest.skipped_tests_tags(branch)
                    }.join(' ')
                    params.CI_PR_REPOS = repos
                    params.BuildPriority = getPriority()
                    if (result.coverage.prebuilt) {
                        params.CI_RPM_TEST_VERSION = daosLatestVersion(branch, result.coverage.versionDistro)
                        if (!params.CI_RPM_TEST_VERSION?.trim()) {
                            error("No prebuilt DAOS version for ${branch}")
                        }
                    }
                    validateParameters(branch, result.coverage.parameters, setup.jenkinsfile)
                    job = 'daos-stack/daos/' + setupDownstreamTesting.test_branch(branch)
                    result.job = job
                }
                List parameters = result.coverage.parameters.collect { name, value ->
                    value instanceof Boolean ? booleanParam(name: name, value: value) :
                                               string(name: name, value: value.toString())
                }
                def downstream = build(job: job, parameters: parameters, propagate: false, wait: true)
                result.result = downstream?.result ?: 'UNKNOWN'
                result.url = downstream?.absoluteUrl
                result.build = downstream?.number
                if (result.result == 'ABORTED') {
                    // Keep collecting the other completed branch results; never allow approval.
                    result.evidenceError = 'Downstream was aborted'
                } else if (result.url) {
                    node('lightweight') {
                        Map evidence = readJSON(text: httpRequest(
                            url: result.url + 'artifact/dependency-validation-evidence.json',
                            quiet: true, timeout: 60).content, returnPojo: true)
                        Map tests = readJSON(text: httpRequest(
                            url: result.url + 'testReport/api/json?tree=suites[enclosingBlockNames,cases[status]]',
                            quiet: true, timeout: 60).content, returnPojo: true)
                        result.evidence = evidence
                        boolean compilerValid = true
                        if (product == 'go-build-toolchain') {
                            // Pipeline environment does not control Dockerfile RUN environments.
                            String console = httpRequest(url: result.url + 'consoleText',
                                quiet: true, consoleLogResponseBody: false, timeout: 60).content
                            compilerValid = console?.trim() &&
                                !(console =~ /(?:^|\s)go: downloading (?:go\d|golang\.org\/toolchain@)/).find()
                            result.compilerAudit = [
                                status: compilerValid ? 'NO_AUTODOWNLOAD_MESSAGES' : 'REJECTED_AUTODOWNLOAD',
                                url: result.url + 'consoleText']
                        }
                        result.testCounts = (tests.suites ?: []).collect { suite ->
                            [stages: suite.enclosingBlockNames,
                             executed: suite.cases.count { it.status != 'SKIPPED' },
                             skipped: suite.cases.count { it.status == 'SKIPPED' }]
                        }
                        result.evidenceValid = compilerValid && evidenceValid(result, evidence, tests, repos)
                        if (result.result == 'SUCCESS' && result.evidenceValid) {
                            setupDownstreamTesting.cleanup('daos-stack/daos', branch)
                        }
                    }
                }
            } catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException interrupted) {
                result.result = 'ABORTED'
                throw interrupted
            } catch (Exception exception) {
                result.evidenceError = exception.toString()
            } finally {
                report(state)
            }
        }
        String action = decision(state.results)
        state.candidateCoverage = distros.collectEntries { distro ->
            [(distro): state.results.findAll { distro in (it.coverage?.compatibleDistros ?: []) }
                .collect { it.branch }]
        }
        if (action == 'block') {
            error('Required validation missing, aborted, skipped, or lacking execution/integrity evidence')
        }
        if (!this.publishBranch([BRANCH_NAME: env.BRANCH_NAME, CHANGE_ID: env.CHANGE_ID], publishBranch)) {
            state.publicationPolicy = 'NOT_A_PUBLISH_BRANCH'
            return
        }
        node('lightweight') {
            dir("candidate-preflight-${env.BUILD_NUMBER}") {
                deleteDir()
                restore(state)
            }
        }
        if (action == 'approve') {
            approve(state)
        }
        publish(state, product, publishBranch)
    } catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException interrupted) {
        state.interrupted = true
        currentBuild.result = 'ABORTED'
        throw interrupted
    } finally {
        if (state.results) {
            String outcome = validationResult(state.results)
            if (decision(state.results) == 'block' && outcome != 'ABORTED') {
                outcome = 'FAILURE'
            }
            if (outcome != 'SUCCESS' && currentBuild.currentResult != 'ABORTED') {
                currentBuild.result = outcome
            }
            state.validationResult = outcome
        }
        report(state)
    }
}
