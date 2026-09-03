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

class TestIsStackTip {

    static final Map MID_STACK = [number: 18980, size: 5, position: 2,
                                  base_ref: 'master', base_sha: '79cfdd87']
    static final Map TOP_OF_STACK = [number: 18980, size: 5, position: 5,
                                     base_ref: 'master', base_sha: '79cfdd87']

    private Script loadScriptWithMocks(Map extraBinding = [:]) {
        Binding binding = new Binding()

        binding.setVariable('env', [:])
        binding.setVariable('params', [:])
        commonBindings(binding)

        binding.setVariable('paramsValue', { String p, Object d -> d })
        binding.setVariable('cachedCommitPragma', { String p, String d = null -> d ?: '' })
        binding.setVariable('prStack', { -> [:] })

        extraBinding.each { k, v ->
            binding.setVariable(k, v)
        }

        GroovyShell shell = new GroovyShell(binding)
        return shell.parse(new File('vars/isStackTip.groovy'))
    }

    @Test
    void 'a pull request that is not stacked needs full verification'() {
        Script script = loadScriptWithMocks([prStack: { -> [:] }])

        assertTrue(script.call())
    }

    @Test
    void 'the top pull request of a stack needs full verification'() {
        Script script = loadScriptWithMocks([prStack: { -> TOP_OF_STACK }])

        assertTrue(script.call())
    }

    @Test
    void 'a mid-stack pull request does not need full verification'() {
        Script script = loadScriptWithMocks([prStack: { -> MID_STACK }])

        assertFalse(script.call())
    }

    @Test
    void 'the bottom pull request of a stack does not need full verification'() {
        Script script = loadScriptWithMocks([
            prStack: { -> [number: 18980, size: 5, position: 1, base_ref: 'master'] }
        ])

        assertFalse(script.call())
    }

    @Test
    void 'the Skip-stack-optimization pragma forces full verification'() {
        Script script = loadScriptWithMocks([
            prStack: { -> MID_STACK },
            cachedCommitPragma: { String p, String d = null ->
                p == 'Skip-stack-optimization' ? 'true' : (d ?: '')
            }
        ])

        assertTrue(script.call())
    }

    @Test
    void 'CI_STACK_TIP_ONLY set to false forces full verification'() {
        Script script = loadScriptWithMocks([
            prStack: { -> MID_STACK },
            paramsValue: { String p, Object d -> p == 'CI_STACK_TIP_ONLY' ? false : d }
        ])

        assertTrue(script.call())
    }
}
