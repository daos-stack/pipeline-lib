/*
 * Copyright 2026 Hewlett Packard Enterprise Development LP
 *
 * SPDX-License-Identifier: BSD-2-Clause-Patent
 */

import static helpers.Bindings.*
import static org.junit.jupiter.api.Assertions.*

import groovy.json.JsonOutput
import groovy.lang.Binding
import groovy.lang.GroovyShell
import org.junit.jupiter.api.Test

class TestPrStack {

    static final String JOB_NAME_MOCK = 'daos-stack/daos/PR-1234'
    static final String CHANGE_ID_MOCK = '1234'

    static final Map STACKED_PR = [
        number: 1234,
        base: [ref: 'soumagne/remove_psm2'],
        stack: [
            id: 788800,
            number: 18980,
            position: 2,
            size: 5,
            base: [ref: 'master', sha: '79cfdd87']
        ]
    ]

    static final Map UNSTACKED_PR = [
        number: 1234,
        base: [ref: 'master']
    ]

    private Script loadScriptWithMocks(Map env, Map extraBinding = [:]) {
        Binding binding = new Binding()

        binding.setVariable('env', env)
        commonBindings(binding)

        binding.setVariable('githubAccess', { -> [] })
        binding.setVariable('withCredentials', { List l, Closure c -> c() })
        binding.setVariable('readJSON', { Map m -> new groovy.json.JsonSlurper().parseText(m.text) })

        extraBinding.each { k, v ->
            binding.setVariable(k, v)
        }

        GroovyShell shell = new GroovyShell(binding)
        return shell.parse(new File('vars/prStack.groovy'))
    }

    private Closure httpRequestReturning(Map pullRequest) {
        return { Map m -> [content: JsonOutput.toJson(pullRequest)] }
    }

    @Test
    void 'a branch build is never stacked'() {
        Script script = loadScriptWithMocks([JOB_NAME: 'daos-stack/daos/master'], [
            httpRequest: { Map m -> throw new RuntimeException('GitHub should not be queried') }
        ])

        assertEquals([:], script.call())
    }

    @Test
    void 'a stacked pull request returns its stack metadata'() {
        Script script = loadScriptWithMocks(
            [JOB_NAME: JOB_NAME_MOCK, CHANGE_ID: CHANGE_ID_MOCK],
            [httpRequest: httpRequestReturning(STACKED_PR)])

        Map stack = script.call()

        assertEquals(18980, stack.number)
        assertEquals(5, stack.size)
        assertEquals(2, stack.position)
        assertEquals('master', stack.base_ref)
        assertEquals('79cfdd87', stack.base_sha)
    }

    @Test
    void 'a pull request that is not in a stack returns an empty map'() {
        Script script = loadScriptWithMocks(
            [JOB_NAME: JOB_NAME_MOCK, CHANGE_ID: CHANGE_ID_MOCK],
            [httpRequest: httpRequestReturning(UNSTACKED_PR)])

        assertEquals([:], script.call())
    }

    @Test
    void 'a failed lookup fails open rather than failing the build'() {
        Script script = loadScriptWithMocks(
            [JOB_NAME: JOB_NAME_MOCK, CHANGE_ID: CHANGE_ID_MOCK],
            [httpRequest: { Map m -> throw new RuntimeException('GET returned 403 Forbidden') }])

        assertEquals([:], script.call())
    }

    @Test
    void 'GitHub is only queried once per build'() {
        int calls = 0
        Closure countingHttpRequest = { Map m ->
            calls++
            return [content: JsonOutput.toJson(STACKED_PR)]
        }

        Script script = loadScriptWithMocks(
            [JOB_NAME: JOB_NAME_MOCK, CHANGE_ID: CHANGE_ID_MOCK],
            [httpRequest: countingHttpRequest])

        script.call([clear: true])
        script.call()
        script.call()
        script.call()

        assertEquals(1, calls)
    }

    @Test
    void 'the repository is taken from the job name'() {
        Script script = loadScriptWithMocks(
            [JOB_NAME: JOB_NAME_MOCK, CHANGE_ID: CHANGE_ID_MOCK],
            [httpRequest: httpRequestReturning(STACKED_PR)])

        assertEquals('daos-stack/daos', script.ghRepo())
    }
}
