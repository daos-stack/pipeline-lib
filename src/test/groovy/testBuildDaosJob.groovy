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

class TestBuildDaosJob {

    static final String SKIP_BUILD = 'Skip-build: true'
    static final String SKIP_TEST = 'Skip-test: true'
    static final String SKIP_TEST_HARDWARE = 'Skip-test-hardware: true'
    static final String SKIP_FAULT_INJECTION = 'Skip-fault-injection-test: true'

    private Script loadScriptWithMocks(Map pragmas = [:]) {

        Binding binding = new Binding()

        binding.setVariable('env', [:])

        commonBindings(binding)

        binding.setVariable('cachedCommitPragma', { String name, String defaultValue = '' ->
            return pragmas.containsKey(name) ? pragmas[name] : defaultValue
        })

        GroovyShell shell = new GroovyShell(binding)
        return shell.parse(new File('vars/buildDaosJob.groovy'))
    }

    private List pragmasFor(Map pragmas = [:]) {
        return loadScriptWithMocks(pragmas).downstreamPragmas()
    }

    @Test
    void 'downstream testing is build only by default'() {
        List pragmas = pragmasFor()

        assertTrue(SKIP_TEST in pragmas, "expected ${SKIP_TEST} in ${pragmas}")
        assertTrue(SKIP_TEST_HARDWARE in pragmas, "expected ${SKIP_TEST_HARDWARE} in ${pragmas}")
        assertTrue(SKIP_FAULT_INJECTION in pragmas, "expected ${SKIP_FAULT_INJECTION} in ${pragmas}")
        assertFalse(SKIP_BUILD in pragmas, "unexpected ${SKIP_BUILD} in ${pragmas}")
    }

    @Test
    void 'hardware and fault injection are skipped alongside the Test stage'() {
        // Skip-test only covers the 'Test' parent stage, so a build only run
        // must always emit the sibling pragmas or those stages still run.
        List pragmas = pragmasFor()

        assertEquals(pragmas.contains(SKIP_TEST), pragmas.contains(SKIP_TEST_HARDWARE))
        assertEquals(pragmas.contains(SKIP_TEST), pragmas.contains(SKIP_FAULT_INJECTION))
    }

    @Test
    void 'Full-downstream-test requests a full downstream run'() {
        List pragmas = pragmasFor(['Full-downstream-test': 'true'])

        assertFalse(SKIP_TEST in pragmas, "unexpected ${SKIP_TEST} in ${pragmas}")
        assertFalse(SKIP_TEST_HARDWARE in pragmas, "unexpected ${SKIP_TEST_HARDWARE} in ${pragmas}")
        assertFalse(SKIP_FAULT_INJECTION in pragmas, "unexpected ${SKIP_FAULT_INJECTION} in ${pragmas}")
    }

    @Test
    void 'Skip-downstream-test overrides Full-downstream-test'() {
        List pragmas = pragmasFor(['Full-downstream-test': 'true',
                                   'Skip-downstream-test': 'true'])

        assertTrue(SKIP_TEST in pragmas, "expected ${SKIP_TEST} in ${pragmas}")
        assertTrue(SKIP_TEST_HARDWARE in pragmas, "expected ${SKIP_TEST_HARDWARE} in ${pragmas}")
    }

    @Test
    void 'Skip-downstream-test remains a no-op on its own'() {
        assertEquals(pragmasFor(), pragmasFor(['Skip-downstream-test': 'true']))
    }

    @Test
    void 'Test-skip-build adds the build skip pragma'() {
        List pragmas = pragmasFor(['Test-skip-build': 'true'])

        assertTrue(SKIP_BUILD in pragmas, "expected ${SKIP_BUILD} in ${pragmas}")
        assertTrue(SKIP_TEST in pragmas, "expected ${SKIP_TEST} in ${pragmas}")
    }

    @Test
    void 'pragmas are joined without leading or trailing newlines'() {
        String joined = pragmasFor().join('\n')

        assertEquals(joined.trim(), joined)
        assertFalse(joined.contains('\n\n'), "unexpected blank line in ${joined}")
    }
}
