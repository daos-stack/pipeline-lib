/*
 * Copyright 2026 Hewlett Packard Enterprise Development LP
 *
 * SPDX-License-Identifier: BSD-2-Clause-Patent
 */

import static helpers.Bindings.*
import static org.junit.jupiter.api.Assertions.*

import groovy.lang.Binding
import groovy.lang.GroovyShell
import org.junit.jupiter.api.Test

class TestNotifySkippedRequiredChecks {

    private List<Map> notified = []

    private Script loadScriptWithMocks(Map env, List<String> required) {
        notified = []
        Binding binding = new Binding()

        binding.setVariable('env', env)
        commonBindings(binding)
        binding.setVariable('targetBranch', { -> 'master' })
        binding.setVariable('githubAccess', { -> [] })
        binding.setVariable('withCredentials', { creds, Closure body -> body() })
        binding.setVariable('httpRequest', { Map config -> [content: '[]'] })
        binding.setVariable('readJSON', { Map config -> required })
        binding.setVariable('scmNotify', { Map config -> notified << config })

        GroovyShell shell = new GroovyShell(binding)
        return shell.parse(new File('vars/notifySkippedRequiredChecks.groovy'))
    }

    private static final List<String> REQUIRED = [
        'continuous-integration/jenkins/pr-head',
        'Linting Summary',
        'build/Build on EL 9',
        'build/Build on Leap 15',
        'test/Unit Test',
        'test/Functional on EL 9',
    ]

    @Test
    void 'a required check whose stage was skipped is published as a success'() {
        Script script = loadScriptWithMocks(
            [JOB_NAME: 'daos-stack/daos/PR-1234', CHANGE_ID: '1234',
             DAOS_JENKINS_NOTIFY_STATUS: 'creds'], REQUIRED)

        script.call(skipped_stages: ['Unit Test', 'Functional on EL 9'],
                    description: 'mid-stack')

        assertEquals(['test/Unit Test', 'test/Functional on EL 9'],
                     notified.collect { it['context'] })
        assertTrue(notified.every { it['status'] == 'SUCCESS' })
        assertTrue(notified.every { it['description'] == 'mid-stack' })
    }

    @Test
    void 'a required check whose stage still runs is left to report itself'() {
        Script script = loadScriptWithMocks(
            [JOB_NAME: 'daos-stack/daos/PR-1234', CHANGE_ID: '1234',
             DAOS_JENKINS_NOTIFY_STATUS: 'creds'], REQUIRED)

        script.call(skipped_stages: ['Unit Test'])

        assertEquals(['test/Unit Test'], notified.collect { it['context'] })
    }

    @Test
    void 'a skipped stage that is not a required check is not published'() {
        Script script = loadScriptWithMocks(
            [JOB_NAME: 'daos-stack/daos/PR-1234', CHANGE_ID: '1234',
             DAOS_JENKINS_NOTIFY_STATUS: 'creds'], REQUIRED)

        script.call(skipped_stages: ['Unit Test bdev with memcheck'])

        assertEquals([], notified)
    }

    @Test
    void 'checks that Jenkins does not publish are never touched'() {
        // GitHub Actions contexts have no test/ or build/ prefix and keep
        // running on a mid-stack pull request, so they must be left alone.
        Script script = loadScriptWithMocks(
            [JOB_NAME: 'daos-stack/daos/PR-1234', CHANGE_ID: '1234',
             DAOS_JENKINS_NOTIFY_STATUS: 'creds'], REQUIRED)

        script.call(skipped_stages: ['Linting Summary',
                                     'continuous-integration/jenkins/pr-head'])

        assertEquals([], notified)
    }

    @Test
    void 'nothing is published when no stage is skipped'() {
        Script script = loadScriptWithMocks(
            [JOB_NAME: 'daos-stack/daos/PR-1234', CHANGE_ID: '1234',
             DAOS_JENKINS_NOTIFY_STATUS: 'creds'], REQUIRED)

        script.call(skipped_stages: [])

        assertEquals([], notified)
    }

    @Test
    void 'nothing is published for a branch build'() {
        Script script = loadScriptWithMocks(
            [JOB_NAME: 'daos-stack/daos/master',
             DAOS_JENKINS_NOTIFY_STATUS: 'creds'], REQUIRED)

        script.call(skipped_stages: ['Unit Test'])

        assertEquals([], notified)
    }

    @Test
    void 'nothing is published when Jenkins cannot notify GitHub'() {
        Script script = loadScriptWithMocks(
            [JOB_NAME: 'daos-stack/daos/PR-1234', CHANGE_ID: '1234'], REQUIRED)

        script.call(skipped_stages: ['Unit Test'])

        assertEquals([], notified)
    }

    @Test
    void 'a failure to read the required checks does not fail the build'() {
        Script script = loadScriptWithMocks(
            [JOB_NAME: 'daos-stack/daos/PR-1234', CHANGE_ID: '1234',
             DAOS_JENKINS_NOTIFY_STATUS: 'creds'], REQUIRED)
        script.getBinding().setVariable('httpRequest', { Map config ->
            throw new RuntimeException('404 Branch not protected')
        })

        script.call(skipped_stages: ['Unit Test'])

        assertEquals([], notified)
    }

    @Test
    void 'a job name that is not a repository pull request publishes nothing'() {
        Script script = loadScriptWithMocks(
            [JOB_NAME: 'PR-1234', CHANGE_ID: '1234',
             DAOS_JENKINS_NOTIFY_STATUS: 'creds'], REQUIRED)

        script.call(skipped_stages: ['Unit Test'])

        assertEquals([], notified)
    }

    @Test
    void 'the stage of a context is the context without its prefix'() {
        Script script = loadScriptWithMocks(
            [JOB_NAME: 'daos-stack/daos/PR-1234', CHANGE_ID: '1234',
             DAOS_JENKINS_NOTIFY_STATUS: 'creds'], REQUIRED)

        assertEquals('Unit Test', script.contextStage('test/Unit Test'))
        assertEquals('Build on EL 9', script.contextStage('build/Build on EL 9'))
        assertEquals('', script.contextStage('Linting Summary'))
        assertEquals('', script.contextStage('continuous-integration/jenkins/pr-head'))
    }
}
