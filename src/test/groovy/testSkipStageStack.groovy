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

class TestSkipStageStack {

    static final Map MID_STACK = [number: 18980, size: 5, position: 2,
                                  base_ref: 'master', base_sha: '79cfdd87']

    private Script loadScriptWithMocks(String stageName, boolean stackTip) {
        Binding binding = new Binding()

        binding.setVariable('env', [STAGE_NAME: stageName])
        binding.setVariable('params', [:])
        commonBindings(binding)

        binding.setVariable('isStackTip', { -> stackTip })
        binding.setVariable('prStack', { -> stackTip ? [:] : MID_STACK })

        GroovyShell shell = new GroovyShell(binding)
        return shell.parse(new File('vars/skipStage.groovy'))
    }

    @Test
    void 'nothing is skipped for the top pull request of a stack'() {
        ['Functional on EL 9', 'Test RPMs on Leap 15', 'Unit Test',
         'Build on Leap 15'].each { stage ->
            Script script = loadScriptWithMocks(stage, true)
            assertFalse(script.skip_mid_stack_pr(stage),
                        stage + ' should not be skipped for the top of a stack')
        }
    }

    @Test
    void 'expensive stages are skipped for a mid-stack pull request'() {
        ['Functional on EL 9', 'Functional Hardware Medium', 'Test RPMs on EL 9',
         'Unit Test', 'Unit Test with memcheck', 'NLT', 'Fault injection testing',
         'Build on Leap 15', 'Build RPM on EL 9'].each { stage ->
            Script script = loadScriptWithMocks(stage, false)
            assertTrue(script.skip_mid_stack_pr(stage),
                       stage + ' should be skipped for a mid-stack pull request')
        }
    }

    @Test
    void 'cheap stages and the EL 9 build still run for a mid-stack pull request'() {
        ['Cancel Previous Builds', 'Pre-build', 'Python Bandit check', 'checkpatch',
         'Lint', 'Check Packaging', 'Build', 'Build on EL 9'].each { stage ->
            Script script = loadScriptWithMocks(stage, false)
            assertFalse(script.skip_mid_stack_pr(stage),
                        stage + ' should still run for a mid-stack pull request')
        }
    }
}
