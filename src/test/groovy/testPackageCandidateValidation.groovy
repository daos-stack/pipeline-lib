/*
 * Copyright 2026 Hewlett Packard Enterprise Development LP
 *
 * SPDX-License-Identifier: BSD-2-Clause-Patent
 */

import static org.junit.jupiter.api.Assertions.*

import groovy.lang.Binding
import groovy.lang.GroovyShell
import org.junit.jupiter.api.Test

class TestPackageCandidateValidation {

    private Script helper(Map overrides = [:]) {
        Map defaults = [
            env: [JOB_NAME: 'daos-stack/go-build-toolchain/PR-42',
                BRANCH_NAME: 'PR-42', BUILD_NUMBER: '17'],
            error: { String message -> throw new IllegalStateException(message) }
        ]
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)
        loader.parseClass('''
            package org.jenkinsci.plugins.workflow.steps
            class FlowInterruptedException extends Exception {}
        ''')
        new GroovyShell(loader, new Binding(defaults + overrides))
            .parse(new File('vars/packageCandidateValidation.groovy'))
    }

    private String declarations(boolean modern) {
        new File('src/test/resources/dependency-validation/' +
            (modern ? 'modern' : 'legacy') + '.Jenkinsfile').text
    }

    @Test
    void 'all three branches are mandatory without caller opt in'() {
        assertEquals(['master', 'release/2.6', 'release/2.8'], helper().branches())
    }

    @Test
    void 'modern selectors disable every hardware and cluster box child and retain rebuild and tags'() {
        Script script = helper()
        ['master', 'release/2.8'].each { branch ->
            String fixture = branch == 'release/2.8' ?
                new File('src/test/resources/dependency-validation/release2.8.Jenkinsfile').text :
                declarations(true)
            Map plan = script.coverage(branch, ['el8', 'el9', 'leap15'],
                ['skip-build': false, 'test-tag': 'daosio'], fixture)
            Map params = plan.parameters
            assertTrue(params.Build)
            assertTrue(params.Build_on_EL_9)
            assertTrue(params.Build_on_Leap_15)
            assertTrue(params.Unit_Tests)
            assertTrue(params.NLT)
            assertEquals('', params.CI_RPM_TEST_VERSION)
            assertTrue(params.Functional_on_EL_9)
            assertTrue(params.Functional_on_Leap_15)
            assertFalse(params.Functional_on_SLES_15)
            assertFalse(params.Functional_on_Ubuntu_20_04)
            assertFalse(params.Fault_injection_testing)
            assertFalse(params.Test_Hardware)
            params.findAll { name, value ->
                name.startsWith('Functional_Hardware_') || name.startsWith('Functional_Cluster_Box_')
            }.each { name, value -> assertFalse(value, name) }
            assertEquals('load_mpi test_core_files daosio', plan.tags)
            assertFalse(params.containsKey('CI_medium_TEST'))
            assertEquals(['Build on EL 9', 'Build on Leap 15'], plan.built)
        }
    }

    @Test
    void 'all ten existing callers retain compatible coverage and their original tags and rebuild policy'() {
        Map callers = [
            'go-build-toolchain': [distros: ['el8', 'el9', 'leap15'], tag: 'daosio', rebuild: true],
            openucx: [distros: ['el8', 'leap15', 'ubuntu20.04'], tag: 'daosio'],
            fio: [distros: ['el8', 'el9', 'leap15'], tag: 'fio'],
            'mpifileutils-pkg': [distros: ['el8', 'el9', 'leap15'], tag: 'mfu'],
            ior: [distros: ['centos7', 'el8', 'el9', 'leap15'], tag: 'ior'],
            mpi4py: [distros: ['el8', 'el9', 'leap15'], tag: 'mpi4py'],
            MACSio: [distros: ['centos7', 'el8', 'el9', 'leap15'], tag: 'macsio'],
            hdf5: [distros: ['el8', 'el9', 'leap15'], tag: 'hdf5testsuite'],
            dpdk: [distros: ['centos7', 'el8', 'el9', 'leap15', 'ubuntu20.04'], tag: ''],
            romio: [distros: ['centos7', 'el8', 'el9', 'leap15'], tag: 'romio']
        ]
        Script script = helper()
        callers.each { name, caller ->
            List original = caller.distros.collect()
            script.branches().each { branch ->
                String fixture = new File('src/test/resources/dependency-validation/' +
                    (branch == 'release/2.6' ? 'legacy' :
                        branch == 'release/2.8' ? 'release2.8' : 'modern') + '.Jenkinsfile').text
                Map args = ['test-tag': caller.tag]
                if (caller.rebuild) {
                    args['skip-build'] = false
                }
                Map plan = script.coverage(branch, caller.distros, args, fixture)
                assertEquals(!caller.rebuild, plan.prebuilt, name)
                assertTrue(plan.tags.endsWith(caller.tag), name)
                assertFalse(plan.functional.isEmpty(), name)
            }
            assertEquals(original, caller.distros, name)
        }
    }

    @Test
    void 'legacy rebuild requires both source and RPM builds with no unstable build override'() {
        Map plan = helper().coverage('release/2.6', ['el8', 'el9', 'leap15'],
            ['skip-build': false], declarations(false))
        assertEquals(['Build on EL 8', 'Build RPM on EL 8', 'Build on EL 9',
            'Build RPM on EL 9', 'Build on Leap 15', 'Build RPM on Leap 15'], plan.built)
        assertFalse(plan.parameters.CI_RPM_el8_NOBUILD)
        assertFalse(plan.parameters.CI_el8_NOBUILD)
        assertTrue(plan.parameters.CI_UNIT_TEST)
        assertTrue(plan.parameters.CI_NLT_TEST)
        ['master', 'release/2.6', 'release/2.8'].each { branch ->
            assertThrows(IllegalStateException) {
                helper().coverage(branch, ['el8', 'el9', 'leap15', 'ubuntu20.04'],
                    ['skip-build': false], declarations(branch != 'release/2.6'))
            }
        }
    }

    @Test
    void 'stage matching cannot accept a missing unit stage just because a similarly named child ran'() {
        Script script = helper()
        assertTrue(script.stageMatches('Unit_Test_on_EL_8_8', 'Unit Test on EL 8'))
        assertTrue(script.stageMatches('Test_RPMs_on_Leap_15_5', 'Test RPMs on Leap 15'))
        assertFalse(script.stageMatches('Unit_Test_bdev', 'Unit Test'))
        assertFalse(script.stageMatches('Unit_Test_with_memcheck', 'Unit Test'))
    }

    @Test
    void 'toolchain instrumentation verifies candidate RPM identity and prohibits auto downloaded compilers'() {
        Script script = helper()
        String fixture = '''    environment { }
                job_step_update(
                    sconsBuild())
                buildRpmPost condition: 'success', rpmlint: true
                jobStatusWrite(job_status_internal)
            '''
        String output = script.instrument(fixture,
            [goIdentities: ['golang-1.26.10-1.x86_64|0123456789abcdef']])
        assertTrue(output.contains("GOTOOLCHAIN = 'local'"))
        assertTrue(output.contains('rpm -qf'))
        assertTrue(output.contains('go env GOVERSION'))
        assertTrue(output.contains('golang-1.26.10-1.x86_64|0123456789abcdef'))
        assertTrue(output.contains('Reject downloaded Go toolchains'))
        assertTrue(output.contains('candidate-compiler-'))
        assertThrows(IllegalStateException) {
            script.compilerCheck(['bad; shell command'])
        }
    }

    @Test
    void 'legacy selectors turn off hardware parent and every child and retain caller distro set'() {
        Map plan = helper().coverage('release/2.6', ['el8', 'leap15', 'ubuntu20.04'],
            ['test-tag': 'fio'], declarations(false))
        Map params = plan.parameters
        assertTrue(params.CI_FUNCTIONAL_HARDWARE_TEST_SKIP)
        params.findAll { name, value ->
            name.startsWith('CI_medium') || name.startsWith('CI_large')
        }.each { name, value -> assertFalse(value, name) }
        assertTrue(params.CI_RPM_el8_NOBUILD)
        assertTrue(params.CI_el8_NOBUILD)
        assertFalse(params.CI_UNIT_TEST)
        assertFalse(params.CI_NLT_TEST)
        assertTrue(params.CI_FUNCTIONAL_el8_TEST)
        assertFalse(params.CI_FUNCTIONAL_el9_TEST)
        assertTrue(params.CI_FUNCTIONAL_leap15_TEST)
        assertTrue(params.CI_FUNCTIONAL_ubuntu20_TEST)
        assertFalse(params.CI_FI_el8_TEST)
        assertFalse(params.containsKey('Test_Hardware'))
    }

    @Test
    void 'prebuilt openucx never schedules EL9 or SLES without matching candidates'() {
        Map plan = helper().coverage('master', ['el8', 'leap15', 'ubuntu20.04'],
            [:], declarations(true))
        assertFalse(plan.parameters.Build)
        assertFalse(plan.parameters.Unit_Tests)
        assertFalse(plan.parameters.Functional_on_EL_9)
        assertTrue(plan.parameters.Functional_on_Leap_15)
        assertTrue(plan.parameters.Functional_on_Ubuntu_20_04)
        assertFalse(plan.parameters.Functional_on_SLES_15)
        assertEquals('leap15', plan.versionDistro)
    }

    @Test
    void 'unsupported candidates and changed branch parameter dialects fail closed'() {
        assertThrows(IllegalStateException) {
            helper().coverage('master', ['centos7', 'ubuntu_rolling'], [:], declarations(true))
        }
        assertThrows(IllegalStateException) {
            helper().coverage('master', ['el9'], [:],
                declarations(true).replace("booleanParam(name: bashName('Test Hardware'))", ''))
        }
    }

    @Test
    void 'every supported artifact distro has an existing publisher route'() {
        Map routes = helper().repositories()
        assertEquals(['centos7', 'el8', 'el9', 'leap15', 'ubuntu20.04', 'ubuntu_rolling'] as Set,
            routes.keySet())
        assertEquals('el-7', routes.centos7.tech)
        assertEquals('el-8', routes.el8.tech)
        assertEquals('el-9', routes.el9.tech)
        assertEquals('leap-15', routes.leap15.tech)
        assertEquals('ubuntu-20.04', routes['ubuntu20.04'].tech)
        assertEquals('ubuntu-rolling', routes.ubuntu_rolling.tech)
        assertEquals('apt', routes.ubuntu_rolling.format)
    }

    @Test
    void 'only all green results or completed bad tests with valid evidence can reach publication decision'() {
        Script script = helper()
        List good = script.branches().collect {
            [branch: it, result: 'SUCCESS', evidenceValid: true]
        }
        assertEquals('publish', script.decision(good))
        ['FAILURE', 'UNSTABLE'].each { result ->
            assertEquals('approve', script.decision(good.collect {
                it + (it.branch == 'master' ? [result: result] : [:])
            }))
        }
        [null, 'NOT_BUILT', 'ABORTED', 'SKIPPED', 'UNKNOWN'].each { result ->
            assertEquals('block', script.decision(good.collect {
                it + (it.branch == 'master' ? [result: result] : [:])
            }))
        }
        assertEquals('block', script.decision(good.take(2)))
        assertEquals('block', script.decision(good + good.take(1)))
        assertEquals('block', script.decision(good.collect {
            it + (it.branch == 'master' ? [evidenceValid: false] : [:])
        }))
    }

    @Test
    void 'approval is tied to an actor and nonblank reason and never changes validation result'() {
        Script script = helper()
        assertTrue(script.validApproval([APPROVER: 'release-admin', JUSTIFICATION: 'Known test bug']))
        [[:], [APPROVER: 'release-admin', JUSTIFICATION: '  '],
            [APPROVER: '', JUSTIFICATION: 'Known issue']].each {
            assertFalse(script.validApproval(it))
        }
        assertEquals('FAILURE', script.validationResult([[result: 'FAILURE'], [result: 'UNSTABLE']]))
        assertEquals('UNSTABLE', script.validationResult([[result: 'UNSTABLE']]))
    }

    @Test
    void 'no approval can permit PR or wrong branch publication'() {
        Script script = helper()
        assertFalse(script.publishBranch([BRANCH_NAME: 'PR-42', CHANGE_ID: '42'], 'PR-42'))
        assertFalse(script.publishBranch([BRANCH_NAME: 'feature'], 'master'))
        assertTrue(script.publishBranch([BRANCH_NAME: 'master'], 'master'))
    }

    @Test
    void 'source instrumentation archives branch evidence and fails on unknown Jenkinsfile layout'() {
        Script script = helper()
        String output = script.instrument('    environment { }\npost { always { jobStatusWrite(job_status_internal) } }')
        assertTrue(output.contains('dependency-validation-evidence.json'))
        assertTrue(output.contains('job_status_internal'))
        assertTrue(output.contains('params.CI_PR_REPOS'))
        assertTrue(output.contains('archiveArtifacts'))
        assertThrows(IllegalStateException) { script.instrument('pipeline { }') }
    }

    @Test
    void 'entrypoint archives all candidates before downstream and has no early stable publication'() {
        String entry = new File('vars/packageBuildingPipelineDAOSTest.groovy').text
        assertFalse(entry.contains('publishToRepository product:'))
        assertEquals(6, entry.count('packageCandidateValidation.archiveCandidate('))
        assertTrue(entry.contains('disableConcurrentBuilds()'))
        assertTrue(entry.contains('agent none'))
        assertTrue(entry.contains('packageCandidateValidation.call('))
    }

    @Test
    void 'entrypoint and generated compiler verification are valid Groovy and shell syntax'() {
        new GroovyShell().parse(new File('vars/packageBuildingPipelineDAOSTest.groovy'))
        List commands = []
        Map bindings = [
            sh: { Map command -> commands << command.script },
            sanitizedStageName: { -> 'Build_on_EL_9' },
            archiveArtifacts: { Map command -> }
        ]
        new GroovyShell(new Binding(bindings)).evaluate(
            helper().compilerCheck(['golang-1.26.10-1.x86_64|0123456789abcdef']))
        assertEquals(1, commands.size())
        Process check = new ProcessBuilder('bash', '-n').redirectErrorStream(true).start()
        check.outputStream.withCloseable { it.write(commands.first().getBytes('UTF-8')) }
        String output = check.inputStream.text
        assertEquals(0, check.waitFor(), output)
        assertTrue(commands.first().contains('} > candidate-compiler-Build_on_EL_9.txt'))
    }

    @Test
    void 'draft library tests pin this branch and unconditionally disable own downstream matrix'() {
        String source = new File('Jenkinsfile').text
        assertTrue(source.readLines().contains(
            "@Library(value='pipeline-lib@ryon-jensen/sre-4139-candidate-validation') _"))
        String matrix = source.substring(source.indexOf("stage('DAOS Build and Test')"))
        assertTrue(matrix.substring(0, matrix.indexOf('matrix {')).contains('expression { false }'))
        assertTrue(source.contains("stage('JUnit Tests')"))
        assertTrue(source.contains('Run unit tests'))
    }

    @Test
    void 'candidate parsing avoids sandbox rejected String findAll overloads'() {
        String source = new File('vars/packageCandidateValidation.groovy').text
        assertFalse(source.contains('block.findAll(/'))
        assertFalse(source.contains('jenkinsfile.findAll(/'))
        Map launch = [job: 'daos-stack/fio/master', build: '17', repositories: 'fio@master:17']
        assertThrows(IllegalStateException) {
            helper().protectLaunch('pipeline { }\npipeline { }', launch)
        }
        assertThrows(IllegalStateException) {
            helper().protectLaunch('// pipeline { }\n', launch)
        }
        assertTrue(helper().protectLaunch('// preamble\npipeline { }\n', launch)
            .contains('getBuildCauses'))
    }

    @Test
    void 'synthetic branch indexing webhook timer and manual builds cannot enter pipeline defaults'() {
        Map launch = [job: 'daos-stack/fio/master', build: '17', repositories: 'fio@feature/$pin:17']
        String guarded = helper().protectLaunch('pipeline { agent() }', launch)
        assertTrue(guarded.indexOf('getBuildCauses') < guarded.indexOf('pipeline {'))
        List cases = [
            [causes: []],
            [causes: [], repositories: ''],
            [causes: [], repositories: launch.repositories],
            [causes: [[upstreamProject: launch.job, upstreamBuild: 18]], repositories: launch.repositories],
            [causes: [[upstreamProject: 'unrelated/master', upstreamBuild: 17]], repositories: launch.repositories],
            [causes: [[upstreamProject: launch.job, upstreamBuild: 17]], repositories: 'fio@master:18'],
            [causes: [[upstreamProject: launch.job, upstreamBuild: 17]], repositories: launch.repositories]
        ]
        cases.eachWithIndex { scenario, index ->
            List entered = []
            new GroovyShell(new Binding([
                currentBuild: new Expando(getBuildCauses: { String type ->
                    assertEquals('hudson.model.Cause$UpstreamCause', type)
                    scenario.causes
                }),
                params: [CI_PR_REPOS: scenario.repositories],
                echo: { String message -> },
                pipeline: { Closure body -> entered << true }
            ])).evaluate(guarded)
            assertEquals(index == cases.size() - 1 ? 1 : 0, entered.size())
        }
        assertThrows(IllegalStateException) { helper().protectLaunch('no pipeline anchor', launch) }
    }
}
