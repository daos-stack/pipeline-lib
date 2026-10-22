/*
 * Copyright 2026 Hewlett Packard Enterprise Development LP
 *
 * SPDX-License-Identifier: BSD-2-Clause-Patent
 */

import static org.junit.jupiter.api.Assertions.*

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.junit.jupiter.api.Test

class TestPackageCandidateWorkflow {

    static class BuildState {
        String result
        boolean keepLog
        String getCurrentResult() { result ?: 'SUCCESS' }
    }

    private Map harness(Map options = [:]) {
        Map state = [sourceRevision: 'candidate-source', candidates: [:]]
        List distros = options.distros ?: ['el8', 'el9', 'leap15']
        distros.each { distro ->
            state.candidates[distro] = [
                sourceRevision: 'candidate-source', sha256: 'a' * 64,
                files: 'original checksum manifest\n',
                rpmIdentities: ['golang-1.26.10-1.x86_64|0123456789abcdef']
            ]
            if (distro.startsWith('ubuntu')) {
                state.candidates[distro].distribution = distro == 'ubuntu20.04' ? 'focal' : 'resolute'
            }
        }
        Map context = [state: state, builds: [], publications: [], reports: [],
            restores: [], commands: [], nodeDepth: 0, approvals: 0, currentBranch: '',
            build: new BuildState(), distros: distros]
        Script script
        Map environment = [
            JOB_NAME: 'daos-stack/go-build-toolchain/master', BUILD_NUMBER: '17',
            BRANCH_NAME: 'master', BUILD_URL: 'https://ci.example/upstream/17/',
            REPOSITORY_URL: 'https://repos.example/', ARTIFACTORY_URL: 'https://artifacts.example/',
            DAOS_DEPENDENCY_APPROVERS: 'release-admin'
        ] + (options.env ?: [:])
        Map binding = [
            env: environment, currentBuild: context.build,
            node: { String label, Closure body ->
                assertEquals('lightweight', label)
                context.nodeDepth++
                try { body() } finally { context.nodeDepth-- }
            },
            dir: { String path, Closure body -> body() },
            lock: { Map config, Closure body ->
                assertTrue(config.resource.startsWith('dependency-publication-'))
                body()
            },
            deleteDir: { -> },
            writeFile: { Map config -> context.commands << config },
            archiveArtifacts: { Map config -> },
            readFile: { String file -> fail("Unexpected read: ${file}") },
            error: { String message -> throw new IllegalStateException(message) },
            cachedCommitPragma: { String name -> options.extraRepos ?: '' },
            getPriority: { -> '3' },
            daosLatestVersion: { String branch, String distro -> '2.8.0-1' },
            string: { Map parameter -> parameter },
            booleanParam: { Map parameter -> parameter },
            specific: { String build -> [build: build] },
            copyArtifacts: { Map config ->
                assertEquals('17', config.selector.build)
                assertEquals(environment.JOB_NAME, config.projectName)
                assertEquals('candidates/**', config.filter)
                context.restores << config
            },
            sh: { Map config ->
                context.commands << config
                if ((options.integrityFailure || (options.integrityAfterApproval && context.approvals)) &&
                    config.label?.startsWith('Verify archived')) {
                    throw new IllegalStateException('checksum mismatch')
                }
                ''
            },
            timeout: { Map config, Closure body ->
                assertEquals([time: 24, unit: 'HOURS'], config)
                assertEquals(0, context.nodeDepth)
                if (options.expireApproval) {
                    throw script.class.classLoader.loadClass(
                        'org.jenkinsci.plugins.workflow.steps.FlowInterruptedException')
                        .getDeclaredConstructor().newInstance()
                }
                body()
            },
            input: { Map config ->
                assertEquals(0, context.nodeDepth, 'approval must be executor free')
                assertEquals('release-admin', config.submitter)
                assertEquals('APPROVER', config.submitterParameter)
                assertTrue(config.message.contains('release/2.8'))
                context.approvals++
                if (options.cancelApproval || options.denyApproval) {
                    throw script.class.classLoader.loadClass(
                        'org.jenkinsci.plugins.workflow.steps.FlowInterruptedException')
                        .getDeclaredConstructor().newInstance()
                }
                options.approval ?: [APPROVER: 'release-admin', JUSTIFICATION: 'Tracked test defect']
            },
            publishToRepository: { Map config ->
                context.publications << config
                assertTrue(config.required)
                assertEquals('stable', config.maturity)
                assertTrue(config.repo_dir.startsWith('artifacts/'))
                if (options.cancelPublication) {
                    throw script.class.classLoader.loadClass(
                        'org.jenkinsci.plugins.workflow.steps.FlowInterruptedException')
                        .getDeclaredConstructor().newInstance()
                }
                if (config.tech == options.failedRepository) {
                    throw new IllegalStateException('repository unavailable')
                }
            },
            packageBuildingPipelineDAOSTest: new Expando(
                skipped_tests_tags: { String branch -> ',-excluded' })
        ]
        binding.setupDownstreamTesting = new Expando(
            call: { String project, String branch, String pragmas, Map setup ->
                context.currentBranch = branch
                assertTrue(pragmas.contains('Skip-func-test-hw: true'))
                assertTrue(pragmas.contains('Skip-fault-injection-test: true'))
                setup.sourceRevision = 'source-' + branch
                setup.testRevision = 'synthetic-' + branch
                setup.jenkinsfile = new File('src/test/resources/dependency-validation/' +
                    (branch == 'release/2.6' ? 'legacy' :
                        branch == 'release/2.8' ? 'release2.8' : 'modern') + '.Jenkinsfile').text
            },
            test_branch: { String branch, Map config -> 'mock-' + branch.replace('/', '-') },
            cleanup: { String project, String branch, Map config -> }
        )
        binding.build = { Map config ->
            assertFalse(config.propagate)
            assertTrue(config.wait)
            context.builds << config
            if (options.cancelBuild && context.currentBranch == 'master') {
                throw script.class.classLoader.loadClass(
                    'org.jenkinsci.plugins.workflow.steps.FlowInterruptedException')
                    .getDeclaredConstructor().newInstance()
            }
            [result: (options.results ?: [:])[context.currentBranch] ?: 'SUCCESS',
                absoluteUrl: "https://ci.example/${context.currentBranch}/18/", number: 18]
        }
        binding.httpRequest = { Map config ->
            if (config.url.endsWith('/consoleText')) {
                if (options.missingCompilerConsole) {
                    throw new IllegalStateException('Compiler console unavailable')
                }
                return [content: options.compilerConsole ?: 'go version go1.26.10 linux/amd64\n']
            }
            String branch = context.currentBranch
            Map result = state.results.find { it.branch == branch }
            Map data
            if (config.url.contains('testReport')) {
                data = [suites: result.coverage.required.collect { name ->
                    [enclosingBlockNames: [name],
                        cases: [[status: options.skippedTests ? 'SKIPPED' : 'PASSED']]]
                }]
            } else {
                Map stages = (result.coverage.required + result.coverage.built).collectEntries {
                    [(it.replaceAll('[ .]', '_')): [result: 'SUCCESS']]
                }
                if (options.missingStage) {
                    stages.remove(stages.keySet().first())
                }
                if (options.hardwareExecuted) {
                    stages.Functional_Cluster_Box_Medium_MD_on_SSD = [result: 'SUCCESS']
                }
                if (options.failedDownstreamBuild) {
                    stages[result.coverage.built.first().replaceAll('[ .]', '_')] = [result: 'FAILURE']
                }
                data = [sourceRevision: options.wrongSource ? 'wrong' : result.sourceRevision,
                    testRevision: result.testRevision,
                    repositories: result.coverage.parameters.CI_PR_REPOS, stages: stages]
            }
            [content: JsonOutput.toJson(data)]
        }
        binding.readJSON = { Map config -> new JsonSlurper().parseText(config.text) }
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)
        loader.parseClass('''
            package org.jenkinsci.plugins.workflow.steps
            class FlowInterruptedException extends Exception {}
        ''')
        script = new GroovyShell(loader, new Binding(binding))
            .parse(new File('vars/packageCandidateValidation.groovy'))
        script.metaClass.setupDownstreamTesting = binding.setupDownstreamTesting.call
        script.metaClass.report = { Map report ->
            context.reports << new JsonSlurper().parseText(JsonOutput.toJson(report))
        }
        context.script = script
        context.execute = { ->
            script.call(options.args ?: ['skip-build': false, 'test-tag': 'daosio'], distros, state,
                'go-build-toolchain', 'master')
        }
        return context
    }

    @Test
    void 'all green runs every downstream with exact archived candidate repository before final publishing'() {
        Map run = harness()
        try {
            run.execute()
        } catch (Exception exception) {
            fail(run.state.results.toString(), exception)
        }
        assertEquals(3, run.builds.size())
        assertEquals(3, run.publications.size())
        assertEquals(0, run.approvals)
        assertEquals(2, run.restores.size(), 'pre-approval and final integrity checks')
        assertTrue(run.build.keepLog)
        run.builds.each { build ->
            assertEquals('go-build-toolchain@master:17',
                build.parameters.find { it.name == 'CI_PR_REPOS' }.value)
            assertEquals('load_mpi,-excluded test_core_files,-excluded daosio,-excluded',
                build.parameters.find { it.name == 'TestTag' }.value)
        }
        assertTrue(run.state.publication.values().every { it.status == 'SUCCESS' })
    }

    @Test
    void 'failure and unstable wait until all three finish and authorized approval preserves bad status'() {
        ['FAILURE', 'UNSTABLE'].each { result ->
            Map run = harness(results: [master: result])
            run.execute()
            assertEquals(3, run.builds.size())
            assertEquals(1, run.approvals)
            assertEquals(3, run.publications.size())
            assertEquals(result, run.build.result)
            assertEquals('release-admin', run.state.approval.actor)
            assertEquals('Tracked test defect', run.state.approval.justification)
            assertEquals('a' * 64, run.state.approval.candidates.el8)
        }
    }

    @Test
    void 'missing coverage aborted skipped and wrong source cannot be manually approved'() {
        [
            [missingStage: true], [skippedTests: true], [wrongSource: true],
            [hardwareExecuted: true], [results: [master: 'ABORTED']],
            [results: [master: 'NOT_BUILT']],
            [results: [master: 'FAILURE'], failedDownstreamBuild: true]
        ].each { options ->
            Map run = harness(options)
            assertThrows(IllegalStateException) { run.execute() }
            assertEquals(3, run.builds.size())
            assertEquals(0, run.approvals)
            assertTrue(run.publications.isEmpty())
        }
    }

    @Test
    void 'candidate build failure and missing candidates never start downstream'() {
        Map run = harness()
        run.state.candidates.remove('el9')
        assertThrows(IllegalStateException) { run.execute() }
        assertTrue(run.builds.isEmpty())
        assertTrue(run.publications.isEmpty())
        run = harness()
        run.build.result = 'FAILURE'
        Map failed = run
        assertThrows(IllegalStateException) { failed.execute() }
        assertTrue(run.builds.isEmpty())
    }

    @Test
    void 'bad archived checksums fail before approval and before any upload'() {
        Map run = harness(results: [master: 'UNSTABLE'], integrityFailure: true)
        assertThrows(IllegalStateException) { run.execute() }
        assertEquals(0, run.approvals)
        assertTrue(run.publications.isEmpty())
    }

    @Test
    void 'blank justification missing policy cancellation and timeout never publish'() {
        Map blank = harness(results: [master: 'FAILURE'],
            approval: [APPROVER: 'release-admin', JUSTIFICATION: '   '])
        assertThrows(IllegalStateException) { blank.execute() }
        assertTrue(blank.publications.isEmpty())
        assertEquals('INVALID', blank.state.approval.status)
        Map noPolicy = harness(results: [master: 'FAILURE'],
            env: [DAOS_DEPENDENCY_APPROVERS: ''])
        assertThrows(IllegalStateException) { noPolicy.execute() }
        assertTrue(noPolicy.publications.isEmpty())
        ['cancelApproval', 'denyApproval', 'expireApproval'].each { condition ->
            Map cancelled = harness([results: [master: 'FAILURE']] + [(condition): true])
            assertThrows(Exception) { cancelled.execute() }
            assertEquals('ABORTED', cancelled.build.result)
            assertTrue(cancelled.publications.isEmpty())
            assertEquals('DENIED_OR_INTERRUPTED', cancelled.state.approval.status)
        }
    }

    @Test
    void 'upstream cancellation is rethrown instead of collected as an ordinary failure'() {
        Map run = harness(cancelBuild: true)
        Exception exception = assertThrows(Exception) { run.execute() }
        assertEquals('FlowInterruptedException', exception.class.simpleName)
        assertEquals(1, run.builds.size())
        assertEquals('ABORTED', run.build.result)
        assertTrue(run.publications.isEmpty())
    }

    @Test
    void 'PR validation never asks for publication approval even after failed tests'() {
        Map run = harness(env: [BRANCH_NAME: 'PR-42', CHANGE_ID: '42'],
            results: [master: 'FAILURE'])
        run.execute()
        assertEquals(3, run.builds.size())
        assertEquals(0, run.approvals)
        assertTrue(run.publications.isEmpty())
        assertEquals('FAILURE', run.build.result)
    }

    @Test
    void 'extra repositories cannot replace the exact candidate with latest or another branch build'() {
        ['go-build-toolchain', 'go-build-toolchain@master',
         'go-build-toolchain@master:18'].each { override ->
            Map run = harness(extraRepos: override)
            assertThrows(IllegalStateException) { run.execute() }
            assertTrue(run.builds.isEmpty())
            assertTrue(run.publications.isEmpty())
        }
    }

    @Test
    void 'Go downloads during image creation or missing compiler console cannot be approved'() {
        [
            [compilerConsole: '#23 2.1 go: downloading go1.26.10 (linux/amd64)\n'],
            [compilerConsole: 'go: downloading golang.org/toolchain@v0.0.1-go1.26.10.linux-amd64\n'],
            [compilerConsole: '   \n'],
            [missingCompilerConsole: true]
        ].each { options ->
            Map run = harness(options + [results: [master: 'UNSTABLE']])
            assertThrows(IllegalStateException) { run.execute() }
            assertEquals(3, run.builds.size())
            assertEquals(0, run.approvals)
            assertTrue(run.publications.isEmpty())
        }
    }

    @Test
    void 'partial upload failure records affected repository and attempts remaining distros without rollback'() {
        Map run = harness(failedRepository: 'el-8')
        assertThrows(IllegalStateException) { run.execute() }
        assertEquals(3, run.publications.size())
        assertEquals('FAILED_POSSIBLY_PARTIAL', run.state.publication.el8.status)
        assertEquals('SUCCESS', run.state.publication.el9.status)
        assertEquals('SUCCESS', run.state.publication.leap15.status)
        assertFalse(run.commands.any { it.script?.contains('delete') })
    }

    @Test
    void 'additional repos cannot shadow this build candidate'() {
        Map run = harness(extraRepos: 'go-build-toolchain@master:99')
        assertThrows(IllegalStateException) { run.execute() }
        assertTrue(run.builds.isEmpty())
        assertTrue(run.publications.isEmpty())
    }

    @Test
    void 'approval cannot bypass a subsequent archive integrity failure'() {
        Map run = harness(results: [master: 'FAILURE'], integrityAfterApproval: true)
        assertThrows(IllegalStateException) { run.execute() }
        assertEquals(1, run.approvals)
        assertEquals('APPROVED', run.state.approval.status)
        assertTrue(run.publications.isEmpty())
    }

    @Test
    void 'publication cancellation stops immediately and records possibly partial upload'() {
        Map run = harness(cancelPublication: true)
        Exception exception = assertThrows(Exception) { run.execute() }
        assertEquals('FlowInterruptedException', exception.class.simpleName)
        assertEquals(1, run.publications.size())
        assertEquals('INTERRUPTED_POSSIBLY_PARTIAL', run.state.publication.el8.status)
        assertEquals('ABORTED', run.build.result)
    }

    @Test
    void 'all six configured distro routes publish archived inputs including explicit DEB distributions'() {
        Map run = harness(distros: ['centos7', 'el8', 'el9', 'leap15', 'ubuntu20.04', 'ubuntu_rolling'],
            args: ['skip-build': true])
        run.execute()
        assertEquals(6, run.publications.size())
        assertEquals('focal', run.publications.find { it.tech == 'ubuntu-20.04' }.distribution)
        assertEquals('resolute', run.publications.find { it.tech == 'ubuntu-rolling' }.distribution)
        assertEquals('apt', run.publications.find { it.tech == 'ubuntu-rolling' }.format)
        assertEquals([], run.state.candidateCoverage.centos7)
        assertEquals([], run.state.candidateCoverage.ubuntu_rolling)
    }
}
