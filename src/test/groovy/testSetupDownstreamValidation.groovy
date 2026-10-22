/*
 * Copyright 2026 Hewlett Packard Enterprise Development LP
 *
 * SPDX-License-Identifier: BSD-2-Clause-Patent
 */

import static org.junit.jupiter.api.Assertions.*

import org.junit.jupiter.api.Test

class TestSetupDownstreamValidation {

    private Map harness() {
        Map context = [commands: [], files: [:]]
        String fixture = new File('src/test/resources/dependency-validation/legacy.Jenkinsfile').text
        context.files['daos-release-2.6-17/Jenkinsfile'] = fixture
        context.files['daos-release-2.6/Jenkinsfile'] = fixture
        Map bindings = [
            JOB_NAME: 'daos-stack/fio/master',
            env: [JOB_NAME: 'daos-stack/fio/master', BUILD_NUMBER: '17'],
            githubAccess: { -> [] },
            withCredentials: { List credentials, Closure body -> body() },
            withEnv: { List environment, Closure body -> body() },
            cachedCommitPragma: { String name, String defaultValue = '' -> defaultValue },
            sh: { Map command ->
                context.commands << command
                if (command.label == 'Delete local test branch') {
                    // The checkout no longer contains the branch-specific Jenkinsfile.
                    context.files['daos-release-2.6-17/Jenkinsfile'] = 'master Jenkinsfile'
                }
                command.returnStdout ? 'revision\n' : ''
            },
            fileExists: { String path -> context.files.containsKey(path) },
            readFile: { String path -> context.files[path] },
            writeFile: { Map command -> context.files[command.file] = command.text },
            booleanParam: { Map parameter -> parameter },
            packageCandidateValidation: new Expando(
                instrument: { String text, Map config -> text + '\n// validation evidence hook' })
        ]
        context.script = new GroovyShell(new Binding(bindings))
            .parse(new File('vars/setupDownstreamTesting.groovy'))
        return context
    }

    @Test
    void 'validation captures branch declarations before local branch cleanup and uses unique personal branches'() {
        Map run = harness()
        Map config = [validation: true]
        run.script.call('daos-stack/daos', 'release/2.6', 'Skip-func-test-hw: true', config)
        assertTrue(config.jenkinsfile.contains('CI_FUNCTIONAL_HARDWARE_TEST_SKIP'))
        assertTrue(config.jenkinsfile.contains('validation evidence hook'))
        assertEquals('revision', config.sourceRevision)
        assertEquals('revision', config.testRevision)
        assertEquals('ryon-jensen-ci-daos-stack-fio-master-17-release-2.6',
            run.script.test_branch('release/2.6', config))
        assertTrue(run.commands.find { it.label == 'Commit and push test branch' }.script.contains('git commit -s'))
        assertFalse(run.commands.any { it.script.contains('env.GH_PASS') })
        assertFalse(run.commands.any { it.script.contains('@github.com') })
        run.commands.each { command ->
            Process check = new ProcessBuilder('bash', '-n').redirectErrorStream(true).start()
            check.outputStream.withCloseable { it.write(command.script.getBytes('UTF-8')) }
            String output = check.inputStream.text
            assertEquals(0, check.waitFor(), output)
        }
    }

    @Test
    void 'unmigrated helper callers retain legacy branch names and return defaults'() {
        Map run = harness()
        List params = run.script.call('daos-stack/daos', 'release/2.6')
        assertEquals('ci-daos-stack-fio-master-release-2.6', run.script.test_branch('release/2.6'))
        assertTrue(params.find { it.name == 'CI_FI_el8_TEST' }.value)
        assertFalse(params.find { it.name == 'CI_medium_TEST' }.value)
        assertFalse(params.find { it.name == 'CI_large_TEST' }.value)
    }

    @Test
    void 'required publisher refuses unavailable infrastructure while other entrypoints remain optional'() {
        Script publisher = new GroovyShell(new Binding([
            env: [:],
            error: { String message -> throw new IllegalStateException(message) }
        ])).parse(new File('vars/publishToRepository.groovy'))
        publisher.call([:])
        assertThrows(IllegalStateException) { publisher.call([required: true]) }
        publisher = new GroovyShell(new Binding([
            env: [REPOSITORY_URL: 'https://repository', BRANCH_NAME: 'master'],
            error: { String message -> throw new IllegalStateException(message) },
            publishToRepositorySystem: { Map config -> throw new NoSuchMethodError('missing site library') }
        ])).parse(new File('vars/publishToRepository.groovy'))
        Script required = publisher
        assertThrows(IllegalStateException) { required.call([required: true]) }
        required.call([:])
    }
}
