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

class TestRunCiShaCheck {

    static final String HEAD = 'd1849ec92eaa784c8f402ea47cb358ca297d08cf'
    static final String OLD = '51be7a0912345678901234567890123456789012'

    List<String> echoes = []

    private Script loadScriptWithMocks(List<String> comments, Map env = [GIT_COMMIT: HEAD]) {
        Binding binding = new Binding()

        commonBindings(binding)
        binding.setVariable('env', env)
        binding.setVariable('echo', { String s -> echoes << s })
        binding.setVariable('error', { String s -> throw new IllegalStateException(s) })
        binding.setVariable('sh', { Map m -> "${HEAD}\n" })

        Expando build = new Expando()
        build.getBuildCauses = { String cls ->
            comments.collect { body -> [_class: cls, commentBody: body] }
        }
        binding.setVariable('currentBuild', build)

        GroovyShell shell = new GroovyShell(binding)
        return shell.parse(new File('vars/runCiShaCheck.groovy'))
    }

    @Test
    void 'a build not started by a comment is not checked'() {
        Script script = loadScriptWithMocks([], [:])

        script.call()
        assertEquals([], echoes)
    }

    @Test
    void 'a bare run-ci builds the current head'() {
        Script script = loadScriptWithMocks(['/run-ci'])

        script.call()
        assertTrue(echoes[0].contains('without a commit SHA'))
    }

    @Test
    void 'a matching full sha builds'() {
        Script script = loadScriptWithMocks(["/run-ci ${HEAD}"])

        script.call()
        assertTrue(echoes[0].contains('matches'))
    }

    @Test
    void 'a matching short uppercase sha builds'() {
        Script script = loadScriptWithMocks(['/run-ci D1849EC'])

        script.call()
        assertTrue(echoes[0].contains('matches'))
    }

    @Test
    void 'a stale sha fails the build'() {
        Script script = loadScriptWithMocks(["/run-ci ${OLD.take(7)}"])

        IllegalStateException e = assertThrows(IllegalStateException) { script.call() }
        assertTrue(e.message.contains(OLD.take(7)))
        assertTrue(e.message.contains(HEAD))
    }

    @Test
    void 'any matching comment among coalesced causes builds'() {
        Script script = loadScriptWithMocks(["/run-ci ${OLD}", '/run-ci d1849ec9'])

        script.call()
        assertTrue(echoes[0].contains('matches'))
    }

    @Test
    void 'an unrecognized comment fails closed'() {
        Script script = loadScriptWithMocks(['/run-ci please'])

        IllegalStateException e = assertThrows(IllegalStateException) { script.call() }
        assertTrue(e.message.contains('no recognized commit'))
    }

    @Test
    void 'the head comes from git when GIT_COMMIT is unset'() {
        Script script = loadScriptWithMocks(['/run-ci d1849ec'], [:])

        script.call()
        assertTrue(echoes[0].contains("Commit ${HEAD} matches"))
    }

    @Test
    void 'parsing accepts only run-ci with an optional 7 to 40 digit sha'() {
        Script script = loadScriptWithMocks([])

        assertEquals('', script.runCiSha('/run-ci'))
        assertEquals('', script.runCiSha('  /run-ci \n'))
        assertEquals('abcdef1', script.runCiSha('/run-ci ABCDEF1'))
        assertEquals('abcdef1', script.runCiSha('/RUN-CI abcdef1'))
        assertNull(script.runCiSha('/run-ci abc'))
        assertNull(script.runCiSha('/run-ci ' + HEAD + '0'))
        assertNull(script.runCiSha('please /run-ci'))
        assertNull(script.runCiSha(null))
    }
}
