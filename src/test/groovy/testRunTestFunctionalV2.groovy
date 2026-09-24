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

class TestRunTestFunctionalV2 {

    Map runTestConfig = null

    private Script loadScript(Map extraBinding = [:]) {
        Binding binding = new Binding()
        commonBindings(binding)
        binding.setVariable('env', [STAGE_NAME: 'stage', OPERATIONS_EMAIL: 'ops'])
        binding.setVariable('parseStageInfo', { Map m -> [pragma_suffix: '-hw'] })
        binding.setVariable('fileExists', { String f -> true })
        binding.setVariable('fileCreateOperation', { Map m -> [:] })
        binding.setVariable('runTest', { Map m ->
            runTestConfig = m.clone()
            return [result_code: 0]
        })

        extraBinding.each { k, v ->
            binding.setVariable(k, v)
        }

        GroovyShell shell = new GroovyShell(binding)
        return shell.parse(new File('vars/runTestFunctionalV2.groovy'))
    }

    private Map baseConfig(Map extra = [:]) {
        return [test_tag: 'pr', ftest_arg: '', pragma_suffix: '-hw', node_count: 2] + extra
    }

    @Test
    void 'detaches the tests when testing RPMs and the branch supports it'() {
        loadScript().call(baseConfig(test_rpms: 'true'))

        assertTrue(runTestConfig.script.startsWith('FTEST_DETACH=true '))
        assertTrue(runTestConfig.script.endsWith('ci/functional/test_main.sh'))
        assertEquals('ci/functional/test_detached.sh poll', runTestConfig.poll_script)
        assertEquals('ci/functional/test_detached.sh kill', runTestConfig.kill_script)
        assertFalse(runTestConfig.containsKey('detach'))
    }

    @Test
    void 'does not detach when disabled'() {
        loadScript().call(baseConfig(test_rpms: 'true', detach: false))

        assertFalse(runTestConfig.script.contains('FTEST_DETACH'))
        assertNull(runTestConfig.poll_script)
        assertFalse(runTestConfig.containsKey('detach'))
    }

    @Test
    void 'does not detach when not testing RPMs'() {
        loadScript().call(baseConfig(test_rpms: 'false'))

        assertFalse(runTestConfig.script.contains('FTEST_DETACH'))
        assertNull(runTestConfig.poll_script)
    }

    @Test
    void 'does not detach when the branch has no detached test script'() {
        Script script = loadScript([
            fileExists: { String f -> f != 'ci/functional/test_detached.sh' }
        ])
        script.call(baseConfig(test_rpms: 'true'))

        assertFalse(runTestConfig.script.contains('FTEST_DETACH'))
        assertNull(runTestConfig.poll_script)
    }
}
