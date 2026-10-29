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

class TestRunTestFunctionalV1 {

    static final String PRAGMA_SUFFIX_MOCK = '-pr'
    static final String TEST_TAG_MOCK = 'pr daily_regression'
    static final int NODE_COUNT_MOCK = 4
    static final String FTEST_ARG_MOCK = '--test_servers srv1,srv2'
    static final String TEST_RPMS_ENABLED = 'true'
    static final String TEST_RPMS_DISABLED = 'false'
    static final int RESULT_CODE_SUCCESS = 0
    static final int RESULT_CODE_MOCK = 17
    static final String STATUS_MOCK = 'Odd status'

    static final List STASHES_MOCK = [
        'build-stash',
        'source-stash'
    ]

    static final String JUNIT_FILES_EXPECTED =
        'install/lib/daos/TESTING/ftest/avocado/job-results/job-*/*.xml ' +
        'install/lib/daos/TESTING/ftest/*_results.xml'

    static final String FAILURE_ARTIFACTS_EXPECTED = 'Functional'

    private Script loadScriptWithMocks(Map extraBinding = [:]) {
        Binding binding = new Binding()

        // ---- PIPELINE STEP MOCKS ----
        commonBindings(binding)

        // ---- INTERNAL LIBRARY STEPS ----
        binding.setVariable('runTest', { Map config ->
            return config
        })

        // Override bindings as required for a specific test.
        extraBinding.each { key, value ->
            binding.setVariable(key, value)
        }

        GroovyShell shell = new GroovyShell(binding)
        return shell.parse(new File('vars/runTestFunctionalV1.groovy'))
    }

    private static Map basicConfig(Map overrides = [:]) {
        Map config = [
            pragma_suffix: PRAGMA_SUFFIX_MOCK,
            test_tag     : TEST_TAG_MOCK,
            node_count   : NODE_COUNT_MOCK,
            ftest_arg    : FTEST_ARG_MOCK,
            test_rpms    : TEST_RPMS_DISABLED,
            stashes      : STASHES_MOCK
        ]

        config.putAll(overrides)
        return config
    }

    private static String expectedScript(String testRpms) {
        return 'ci/functional/run_test_functional_v1.sh ' +
            "\"${PRAGMA_SUFFIX_MOCK}\" " +
            "\"${TEST_TAG_MOCK}\" " +
            "\"${NODE_COUNT_MOCK}\" " +
            "\"${FTEST_ARG_MOCK}\" " +
            "\"${testRpms}\""
    }

    @Test
    void 'runTest() gets functional test script arguments'() {
        Closure runTest = { Map config ->
            assertNotNull(config)
            assertEquals(
                expectedScript(TEST_RPMS_DISABLED),
                config.script
            )

            return [result_code: RESULT_CODE_SUCCESS]
        }

        Script script = loadScriptWithMocks([
            runTest: runTest
        ])

        Map result = script.call(basicConfig())

        assertEquals(RESULT_CODE_SUCCESS, result.result_code)
    }

    @Test
    void 'runTest() gets artifact configuration'() {
        Closure runTest = { Map config ->
            assertNotNull(config)
            assertEquals(JUNIT_FILES_EXPECTED, config.junit_files)
            assertEquals(
                FAILURE_ARTIFACTS_EXPECTED,
                config.failure_artifacts
            )

            return [result_code: RESULT_CODE_SUCCESS]
        }

        Script script = loadScriptWithMocks([
            runTest: runTest
        ])

        Map result = script.call(basicConfig())

        assertEquals(RESULT_CODE_SUCCESS, result.result_code)
    }

    @Test
    void 'stashes are preserved when testing without RPMs'() {
        Closure runTest = { Map config ->
            assertEquals(STASHES_MOCK, config.stashes)

            return [result_code: RESULT_CODE_SUCCESS]
        }

        Script script = loadScriptWithMocks([
            runTest: runTest
        ])

        Map result = script.call(basicConfig())

        assertEquals(RESULT_CODE_SUCCESS, result.result_code)
    }

    @Test
    void 'stashes are removed when testing with RPMs'() {
        Closure runTest = { Map config ->
            assertFalse(config.containsKey('stashes'))

            return [result_code: RESULT_CODE_SUCCESS]
        }

        Script script = loadScriptWithMocks([
            runTest: runTest
        ])

        Map result = script.call(basicConfig([
            test_rpms: TEST_RPMS_ENABLED
        ]))

        assertEquals(RESULT_CODE_SUCCESS, result.result_code)
    }

    @Test
    void 'functional test script gets RPM flag when testing with RPMs'() {
        Closure runTest = { Map config ->
            assertNotNull(config)
            assertEquals(
                expectedScript(TEST_RPMS_ENABLED),
                config.script
            )

            return [result_code: RESULT_CODE_SUCCESS]
        }

        Script script = loadScriptWithMocks([
            runTest: runTest
        ])

        Map result = script.call(basicConfig([
            test_rpms: TEST_RPMS_ENABLED
        ]))

        assertEquals(RESULT_CODE_SUCCESS, result.result_code)
    }

    @Test
    void 'internal functional test parameters are not passed to runTest'() {
        Closure runTest = { Map config ->
            assertFalse(config.containsKey('pragma_suffix'))
            assertFalse(config.containsKey('test_tag'))
            assertFalse(config.containsKey('node_count'))
            assertFalse(config.containsKey('ftest_arg'))
            assertFalse(config.containsKey('test_rpms'))

            return [result_code: RESULT_CODE_SUCCESS]
        }

        Script script = loadScriptWithMocks([
            runTest: runTest
        ])

        Map result = script.call(basicConfig())

        assertEquals(RESULT_CODE_SUCCESS, result.result_code)
    }

    @Test
    void 'call() returns runTest result'() {
        Map expectedResult = [
            result_code: RESULT_CODE_MOCK,
            status     : STATUS_MOCK
        ]

        Closure runTest = { Map config ->
            assertNotNull(config)
            return expectedResult
        }

        Script script = loadScriptWithMocks([
            runTest: runTest
        ])

        Map actualResult = script.call(basicConfig())

        assertEquals(expectedResult, actualResult)
    }
}
