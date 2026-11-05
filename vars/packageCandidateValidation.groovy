/*
 * Copyright 2026 Hewlett Packard Enterprise Development LP
 *
 * SPDX-License-Identifier: BSD-2-Clause-Patent
 */

/* Policies, launch guards, artifact checks and evidence validation for
 * VM-only DAOS dependency candidate validation and deferred publication.
 */

/** Update this expected downstream target list and keep the Declarative matrix axis in sync. */
List branches() {
    return ['master', 'release/2.6', 'release/2.8']
}

Map repositories() {
    return [centos7: [format: 'yum', tech: 'el-7'],
            el8: [format: 'yum', tech: 'el-8'], el9: [format: 'yum', tech: 'el-9'],
            leap15: [format: 'yum', tech: 'leap-15'],
            'ubuntu20.04': [format: 'apt', tech: 'ubuntu-20.04'],
            ubuntu_rolling: [format: 'apt', tech: 'ubuntu-rolling']]
}

/**
 * Read supported one-line parameter declarations without taking selection
 * policy from target defaults. This regex scanner is not a Groovy parser:
 * multiline declarations, computed names, and other arbitrary Groovy forms
 * are unsupported and fail validation rather than being inferred.
 */
Map declarations(String source) {
    Map declared = [:]
    String block = source.split(/(?m)^\s*parameters\s*\{/, 2).last()
    block = block.split(/(?m)^\s*stages\s*\{/, 2).first()
    block.readLines().each { line ->
        def match = line =~ /^\s*(booleanParam|string|choice|text|password)\(name:\s*(?:bashName\('([^']+)'\)|'([^']+)')/
        if (match.find()) {
            String name = match.group(2) ? match.group(2).replaceAll('[^a-zA-Z0-9]', '_') : match.group(3)
            declared[name] = match.group(1)
        }
    }
    return declared
}

/** Require one complete, unambiguous cell result for every expected branch. */
List aggregate(List records, List expected) {
    Map byBranch = [:]
    records.each { record ->
        if (!(record instanceof Map) || !(record.branch in expected)) {
            error("Unexpected or malformed dependency-validation result: ${record}")
        }
        if (byBranch.containsKey(record.branch)) {
            error("Duplicate dependency-validation result for ${record.branch}")
        }
        if (!record.completed || !record.url || record.cleanupError ||
            !(record.result in ['SUCCESS', 'FAILURE', 'UNSTABLE'])) {
            error("Incomplete dependency validation for ${record.branch}: ${record}")
        }
        byBranch[record.branch] = record
    }
    if (byBranch.keySet() != expected.toSet()) {
        error("Missing dependency-validation results: expected ${expected}, found ${byBranch.keySet()}")
    }
    return expected.collect { byBranch[it] }
}

/** Configure every requested control explicitly for each supported DAOS branch. */
Map configuredParameters(String branch, boolean rebuild, boolean units, List distros) {
    Map modern = [
        CI_IGNORE_SKIP_COMMIT_PRAGMAS: false, Cancel_Previous_Builds: false,
        Pre_build: rebuild, Python_Bandit_check: rebuild, Build: rebuild,
        Build_on_EL_9: rebuild && 'el9' in distros,
        Build_on_Leap_15: rebuild && 'leap15' in distros,
        Unit_Tests: units, Unit_Test: units, Unit_Test_bdev: units, NLT: units,
        Unit_Test_with_memcheck: units, Unit_Test_bdev_with_memcheck: units,
        Test: true, Test_Hardware: false,
        Functional_on_EL_9: 'el9' in distros, Functional_on_Leap_15: 'leap15' in distros,
        Functional_on_Ubuntu_20_04: 'ubuntu20.04' in distros,
        Functional_on_SLES_15: false, Functional_on_EL_9_with_Valgrind: false,
        Fault_injection_testing: false,
        Test_RPMs_on_EL_9: rebuild && 'el9' in distros,
        Test_RPMs_on_Leap_15: rebuild && 'leap15' in distros,
        Functional_Hardware_Medium: false, Functional_Hardware_Medium_MD_on_SSD: false,
        Functional_Hardware_Medium_VMD: false, Functional_Hardware_Large: false,
        Functional_Hardware_Large_MD_on_SSD: false
    ]
    Map policies = [
        master: modern + [
            Functional_Hardware_Medium_Provider: false,
            Functional_Hardware_Medium_Provider_MD_on_SSD: false,
            Functional_Cluster_Box_Medium_MD_on_SSD: false,
            Functional_Cluster_Box_Medium_Provider_MD_on_SSD: false],
        'release/2.8': modern + [
            Functional_Hardware_Medium_Verbs_Provider: false,
            Functional_Hardware_Medium_Verbs_Provider_MD_on_SSD: false,
            Functional_Hardware_Medium_UCX_Provider: false,
            Functional_Cluster_Box_Medium_MD_on_SSD: false,
            Functional_Cluster_Box_Medium_Verbs_Provider_MD_on_SSD: false],
        'release/2.6': [
            CI_CANCEL_PREV_BUILD_SKIP: true, CI_UNIT_TEST: units, CI_NLT_TEST: units,
            CI_UNIT_TEST_MEMCHECK: units, CI_FI_el8_TEST: false,
            CI_FUNCTIONAL_TEST_SKIP: false, CI_FUNCTIONAL_HARDWARE_TEST_SKIP: true,
            CI_MORE_FUNCTIONAL_PR_TESTS: true, CI_FUNCTIONAL_el8_VALGRIND_TEST: false,
            CI_TEST_EL8_RPMs: rebuild && 'el8' in distros,
            CI_TEST_LEAP15_RPMs: rebuild && 'leap15' in distros,
            CI_el8_NOBUILD: !rebuild || !('el8' in distros),
            CI_el9_NOBUILD: !rebuild || !('el9' in distros),
            CI_leap15_NOBUILD: !rebuild || !('leap15' in distros),
            CI_RPM_el8_NOBUILD: !rebuild || !('el8' in distros),
            CI_RPM_el9_NOBUILD: !rebuild || !('el9' in distros),
            CI_RPM_leap15_NOBUILD: !rebuild || !('leap15' in distros),
            CI_DEB_Ubuntu20_NOBUILD: !rebuild || !('ubuntu20.04' in distros),
            CI_FUNCTIONAL_el8_TEST: 'el8' in distros,
            CI_FUNCTIONAL_el9_TEST: 'el9' in distros,
            CI_FUNCTIONAL_leap15_TEST: 'leap15' in distros,
            CI_FUNCTIONAL_ubuntu20_TEST: 'ubuntu20.04' in distros,
            CI_medium_TEST: false, CI_medium_md_on_ssd_TEST: false, CI_medium_vmd_TEST: false,
            CI_medium_verbs_provider_TEST: false, CI_medium_verbs_provider_md_on_ssd_TEST: false,
            CI_medium_ucx_provider_TEST: false, CI_large_TEST: false, CI_large_md_on_ssd_TEST: false]
    ]
    if (!policies.containsKey(branch)) {
        error("No configured DAOS validation parameters for ${branch}")
    }
    return [CI_BUILD_PACKAGES_ONLY: false, CI_ALLOW_UNSTABLE_TEST: true,
            CI_RPM_TEST_VERSION: '', CI_PROVISIONING_POOL: 'default',
            CI_BUILD_DESCRIPTION: 'Dependency validation (VM only)'] + policies[branch]
}

/** Fail closed when target declarations differ from the configured policy. */
void validateParameters(String branch, Map selected, String source) {
    Map declared = declarations(source)
    selected.each { name, value ->
        String type = value instanceof Boolean ? 'booleanParam' : 'string'
        if (declared[name] != type) {
            error("DAOS ${branch} parameter ${name}: expected ${type}, found ${declared[name] ?: 'missing'}")
        }
    }
    declared.each { name, type ->
        if (name == 'Test_Hardware' || name == 'CI_FUNCTIONAL_HARDWARE_TEST_SKIP' ||
            name.startsWith('Functional_Hardware_') || name.startsWith('Functional_Cluster_Box_') ||
            name.startsWith('CI_medium') || name.startsWith('CI_large') ||
            name.startsWith('CI_FUNCTIONAL_HARDWARE_') || name.startsWith('CI_CLUSTER_BOX_')) {
            if (!selected.containsKey(name) || selected[name] != (name == 'CI_FUNCTIONAL_HARDWARE_TEST_SKIP')) {
                error("DAOS ${branch} hardware/cluster-box control ${name} has no configured disable policy")
            }
        }
    }
}

/** Preserve caller settings and list the coverage that must actually execute. */
Map coverage(String branch, List distros, Map args, String source) {
    boolean modern = branch != 'release/2.6'
    boolean rebuild = !args.get('skip-build', true)
    List compatible = (modern ? ['el9', 'leap15', 'ubuntu20.04'] :
        ['el8', 'el9', 'leap15', 'ubuntu20.04']).findAll { it in distros }
    if (!compatible || (rebuild && 'ubuntu20.04' in compatible)) {
        error("Unsupported DAOS ${branch} candidate distros/rebuild combination: ${distros}")
    }
    String primary = modern ? 'el9' : 'el8'
    boolean units = rebuild && primary in compatible
    Map selected = configuredParameters(branch, rebuild, units, compatible)
    validateParameters(branch, selected, source)
    List built = []
    List required = []
    Map labels = modern ? [el9: 'EL 9', leap15: 'Leap 15'] :
                          [el8: 'EL 8', el9: 'EL 9', leap15: 'Leap 15']
    labels.each { distro, label ->
        if (rebuild && distro in compatible) {
            built << "Build on ${label}".toString()
            if (!modern) {
                built << "Build RPM on ${label}".toString()
            }
            if (modern || distro != 'el9') {
                required << "Test RPMs on ${label}".toString()
            }
        }
    }
    if (units) {
        required += ['Unit Test', 'Unit Test bdev', 'NLT',
                     'Unit Test with memcheck', 'Unit Test bdev with memcheck'].collect {
            modern ? it : it + ' on EL 8'
        }
    }
    (labels + ['ubuntu20.04': 'Ubuntu 20.04']).each { distro, label ->
        if (distro in compatible) {
            required << "Functional on ${label}".toString()
        }
    }
    return [parameters: selected, built: built, required: required, prebuilt: !rebuild,
            versionDistro: primary in compatible ? primary : compatible.first(),
            tags: ('load_mpi test_core_files ' + args.get('test-tag', '')).trim()]
}

/** A setup invocation registers only verified controls and never enters the DAOS pipeline. */
boolean launch(String encoded) {
    Map expected = new groovy.json.JsonSlurperClassic().parseText(encoded)
    if (!currentBuild.getBuildCauses('hudson.model.Cause$UpstreamCause').any {
        it.upstreamProject == expected.job && it.upstreamBuild.toString() == expected.build
    }) {
        echo 'Ignoring unrelated or automatic dependency-validation launch'
        return false
    }
    if (params.DEPENDENCY_VALIDATION_SETUP != false || params.DEPENDENCY_VALIDATION_ID != expected.id) {
        List definitions = expected.parameters.collect { name, value ->
            value instanceof Boolean ?
                booleanParam(name: name, defaultValue: name.endsWith('_NOBUILD') ||
                    name in ['CI_FUNCTIONAL_HARDWARE_TEST_SKIP', 'CI_FUNCTIONAL_TEST_SKIP',
                             'CI_CANCEL_PREV_BUILD_SKIP']) :
                string(name: name, defaultValue: '')
        }
        definitions += [booleanParam(name: 'DEPENDENCY_VALIDATION_SETUP', defaultValue: true),
                        string(name: 'DEPENDENCY_VALIDATION_ID', defaultValue: expected.id)]
        // The properties step preserves properties not managed by this step.
        properties([parameters(definitions)])
        echo 'Dependency-validation parameters registered; no test agents allocated'
        return false
    }
    if (!expected.parameters.every { name, value -> params[name] == value }) {
        error('Dependency-validation launch parameters do not match the verified upstream request')
    }
    return true
}

/** Check archives at collection and again after restoring the current build for publication. */
Map checkArtifacts(String distro, boolean captureDistribution = true) {
    Map route = repositories()[distro]
    if (!route) {
        error("Unsupported package distro: ${distro}")
    }
    boolean rpm = route.format == 'yum'
    sh label: "Check ${distro} packages", script: """set -eu
        test -n "\$(find artifacts/${distro} -type f -name '${rpm ? '*.rpm' : '*.deb'}' ! -name '*.src.rpm' -print -quit)"
        test -n "\$(find artifacts/${distro} -type f -name '${rpm ? '*.src.rpm' : '*.dsc'}' -print -quit)"
        test -s artifacts/${distro}/${rpm ? 'repodata/repomd.xml' : 'Packages.gz'}
    """
    Map context = [:]
    if (!rpm && captureDistribution) {
        context.distribution = sh(script: '. /etc/os-release; printf "%s" "$VERSION_CODENAME"',
                                  returnStdout: true).trim()
        if (!(context.distribution ==~ /[a-z][a-z0-9-]*/)) {
            error("Missing DEB distribution for ${distro}")
        }
    }
    return context
}

/** Require completed stage execution and non-skipped tests, not merely a green build. */
boolean completed(Map result, Map coverage) {
    if (!(result.result in ['SUCCESS', 'FAILURE', 'UNSTABLE'])) {
        return false
    }
    Map flow = readJSON(text: httpRequest(url: result.url + 'wfapi/describe',
        quiet: true, timeout: 60).content, returnPojo: true)
    Map tests = readJSON(text: httpRequest(url: result.url + 'testReport/api/json?tree=' +
        URLEncoder.encode('suites[enclosingBlockNames,cases[status]]', 'UTF-8'),
        quiet: true, timeout: 60).content, returnPojo: true)
    List stages = flow.stages ?: []
    if (stages.any { stage ->
        (stage.name == 'Test Hardware' || stage.name.startsWith('Functional Hardware') ||
         stage.name.startsWith('Functional Cluster Box') || stage.name.startsWith('Fault injection testing')) &&
        stage.status != 'NOT_EXECUTED'
    }) {
        return false
    }
    return (coverage.built + coverage.required).every { name ->
        List matches = stages.findAll { coverageStage(it.name) == name }
        matches && matches.every {
            it.status in (name in coverage.built ? ['SUCCESS'] : ['SUCCESS', 'FAILED', 'UNSTABLE'])
        }
    } && coverage.required.findAll { !it.startsWith('Test RPMs on ') }.every { name ->
        (tests.suites ?: []).any { suite ->
            suite.enclosingBlockNames?.any { coverageStage(it) == name } &&
            suite.cases?.any { it.status in ['PASSED', 'FIXED', 'FAILED', 'REGRESSION'] }
        }
    }
}

/** Legacy stages use different distro minor versions within the same pipeline. */
String coverageStage(String name) {
    return name.replaceAll(/(EL [89]|Leap 15)\.\d+/, '$1')
}

/** Wait outside any node for an authorized, auditable override of completed test failures. */
Map approve(List results) {
    String submitters = env.DAOS_DEPENDENCY_APPROVERS?.trim()
    if (!submitters || !(submitters ==~ /[A-Za-z0-9_.@-]+(,[A-Za-z0-9_.@-]+)*/)) {
        error('Manual publication requires configured DAOS_DEPENDENCY_APPROVERS')
    }
    Map answer = timeout(time: 24, unit: 'HOURS') {
        input message: 'Publish despite completed dependency test failures?\n' +
            results.collect { "${it.branch}: ${it.result} ${it.url}" }.join('\n'),
            ok: 'Approve publication', submitter: submitters, submitterParameter: 'APPROVER',
            parameters: [string(name: 'JUSTIFICATION', defaultValue: '',
                                 description: 'Required reason for overriding completed test failures')]
    }
    if (!answer.APPROVER?.trim() || !answer.JUSTIFICATION?.trim()) {
        error('Publication requires an identified approver and nonblank justification')
    }
    return [actor: answer.APPROVER, justification: answer.JUSTIFICATION.trim()]
}
