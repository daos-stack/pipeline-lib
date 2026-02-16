/*
 * (C) Copyright 2026 Hewlett Packard Enterprise Development LP
 *
 * SPDX-License-Identifier: BSD-2-Clause-Patent
 */

import static helpers.Asserts.*
import static helpers.Bindings.*
import static org.junit.jupiter.api.Assertions.*

import groovy.lang.Binding
import groovy.lang.GroovyShell
import java.util.stream.Stream
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class UnitTest {

    static final String CONTEXT_MOCK = 'ctx'
    static final String DESCRIPTION_MOCK = 'desc'
    static final int DURATION_SECONDS_MOCK = 13
    static final String JUNIT_FILES_DEFAULT = 'test_results/*.xml'
    static final String NODELIST_MOCK = 'node1'
    static final int NODE_COUNT_MOCK = 57
    static final String INST_RPMS_DEFAULT = ''
    static final String IMAGE_VERSION_MOCK = 'Odd image version'
    static final String BUILD_TYPE_MOCK = 'Odd build type'
    static final String COMPILER_MOCK = 'Odd compiler'
    static final String SANITIZED_STAGE_NAME_MOCK = 'odd_stage_name'
    static final List STASHES_MOCK = [
        'Odd stash 1',
        'Odd stash 2'
    ]
    static final int TIMEOUT_TIME_MOCK = 48
    static final String TIMEOUT_UNIT_MOCK = 'Eon'

    private Script loadScriptWithMocks(Map extraBinding = [:]) {

        def binding = new Binding()

        // ---- ENV ----
        binding.setVariable('env', [
            NODELIST        : UnitTest.NODELIST_MOCK
        ])

        // ---- PIPELINE STEP MOCKS ----
        commonBindings(binding)

        // ---- INTERNAL LIBRARY STEPS ----

        binding.setVariable('provisionNodes', { Map m ->
            [:]
        })

        binding.setVariable('runTest', { Map m ->
            [result_code: 0]
        })

        binding.setVariable('parseStageInfo', { Map m ->
            [
                node_count      : UnitTest.NODE_COUNT_MOCK
            ]
        })

        binding.setVariable('durationSeconds', { Long l -> DURATION_SECONDS_MOCK })
        binding.setVariable('sanitizedStageName', { -> SANITIZED_STAGE_NAME_MOCK })
        binding.setVariable('checkJunitFiles', { Map m -> 'SUCCESS' })

        // override bindings as required for a specific test
        extraBinding.each { k, v ->
            binding.setVariable(k, v)
        }

        def shell = new GroovyShell(binding)
        return shell.parse(new File('vars/unitTest.groovy'))
    }

    @Test
    void 'provisionNodes() gets basic arguments'() {
        def provisionNodes = { Map m ->
            assertNotNull(m)
            assertEquals(UnitTest.NODELIST_MOCK, m.NODELIST)
            assertEquals(UnitTest.NODE_COUNT_MOCK, m.node_count)
            assertEquals(UnitTest.IMAGE_VERSION_MOCK, m.distro)
            assertEquals(UnitTest.INST_RPMS_DEFAULT, m.inst_rpms)

            return [:]
        }

        def script = loadScriptWithMocks([
            provisionNodes: provisionNodes
        ])

        script.call([
            /*
             * It is not the default path but it is the simpler one.
             * The default is tested later on.
             */
            image_version: UnitTest.IMAGE_VERSION_MOCK
        ])
    }

    @Test
    void 'runTest() gets basic arguments'() {
        def runTest = { Map m ->
            assertNotNull(m)
            assertEquals(UnitTest.STASHES_MOCK, m.stashes)
            // assertEquals(WIP, m.script)
            assertEquals(UnitTest.JUNIT_FILES_DEFAULT, m.junit_files)
            assertEquals(UnitTest.CONTEXT_MOCK, m.context)
            assertEquals(UnitTest.DESCRIPTION_MOCK, m.description)
            assertTrue(m.ignore_failure)
            assertFalse(m.notify_result)

            return [:]
        }

        def script = loadScriptWithMocks([
            runTest: runTest
        ])

        script.call([
            /*
             * It is not the default path but it is the simpler one.
             * The default is tested later on.
             */
            stashes: UnitTest.STASHES_MOCK,
            context: UnitTest.CONTEXT_MOCK,
            description: UnitTest.DESCRIPTION_MOCK
        ])
    }

    @Test
    void 'timeout gets correct parameters'() {
        // Defaults
        int expectedTime = 120
        String expectedUnit = 'MINUTES'

        def timeout = { Map m, Closure c ->
            assertNotNull(m)
            assertEquals(expectedTime, m.time)
            assertEquals(expectedUnit, m.unit)
            c()
        }

        def script = loadScriptWithMocks([
            timeout: timeout,
            runTest: { Map cfg ->
                [result_code: 0]
            }
        ])

        script.call([]) // use defaults

        // custom values
        expectedTime = TIMEOUT_TIME_MOCK
        expectedUnit = TIMEOUT_UNIT_MOCK

        script.call([
            timeout_time: TIMEOUT_TIME_MOCK,
            timeout_unit: TIMEOUT_UNIT_MOCK
        ])
    }

    @Test
    void 'call() returns correct runData'() {
        Map set1 = [
            1: 2
        ]
        Map set2 = [
            3: 4,
            result_code: 0
        ]

        def provisionNodes = { Map m ->
            return set1.clone()
        }
        def runTest = { Map m ->
            return set2.clone()
        }

        def script = loadScriptWithMocks([
            provisionNodes: provisionNodes,
            runTest: runTest,
        ])

        def runData = script.call([:])

        assertIsSubset(set1, runData)
        assertIsSubset(set2, runData)
        assert('unittest_time' in runData)
        assertEquals(runData['unittest_time'], DURATION_SECONDS_MOCK)
    }
}
