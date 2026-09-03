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

class TestTargetBranch {

    private Script loadScriptWithMocks(Map env, Closure prStack) {
        Binding binding = new Binding()

        binding.setVariable('env', env)
        commonBindings(binding)
        binding.setVariable('prStack', prStack)

        GroovyShell shell = new GroovyShell(binding)
        return shell.parse(new File('vars/targetBranch.groovy'))
    }

    @Test
    void 'a branch build lands on the branch being built'() {
        Script script = loadScriptWithMocks([BRANCH_NAME: 'master'], { -> [:] })

        assertEquals('master', script.call())
    }

    @Test
    void 'an unstacked pull request lands on its target branch'() {
        Script script = loadScriptWithMocks(
            [BRANCH_NAME: 'PR-1234', CHANGE_TARGET: 'release/2.8'], { -> [:] })

        assertEquals('release/2.8', script.call())
    }

    @Test
    void 'a stacked pull request lands on the base of the stack'() {
        // The pull request directly targets the branch of the layer below it,
        // but the whole stack lands on master.
        Script script = loadScriptWithMocks(
            [BRANCH_NAME: 'PR-1234', CHANGE_TARGET: 'soumagne/remove_psm2'],
            { -> [number: 18980, size: 5, position: 5, base_ref: 'master'] })

        assertEquals('master', script.call())
    }

    @Test
    void 'a stack onto a release branch lands on that release branch'() {
        Script script = loadScriptWithMocks(
            [BRANCH_NAME: 'PR-1234', CHANGE_TARGET: 'someone/layer-1'],
            { -> [number: 18980, size: 3, position: 2, base_ref: 'release/2.8'] })

        assertEquals('release/2.8', script.call())
    }
}
