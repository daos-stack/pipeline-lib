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

class TestDockerBuildArgs {

    static final String BUILD_AGENT_UID_MOCK = '1101'
    static final String JENKINS_URL_MOCK = 'https://jenkins.example.com/'
    static final long BUILD_START_TIME_MOCK = 123456789L
    static final String PROCESSOR_COUNT_MOCK = '16'

    private Script loadScriptWithMocks(
        Map extraBinding = [:],
        List<Map> shCalls = null
    ) {
        Binding binding = new Binding()
        List<Map> recordedShCalls = shCalls != null ? shCalls : []

        binding.setVariable('env', [
            JENKINS_URL: JENKINS_URL_MOCK,
            STAGE_NAME : 'Build'
        ])

        commonBindings(binding)

        binding.setVariable('currentBuild', [
            startTimeInMillis: BUILD_START_TIME_MOCK
        ])

        binding.setVariable('sh', { Map args ->
            assertNotNull(args)
            assertTrue(args.returnStdout)
            recordedShCalls << args

            switch (args.label) {
            case 'getuid()':
                return "${BUILD_AGENT_UID_MOCK}\n"
            case 'Get number of processors online':
                return "${PROCESSOR_COUNT_MOCK}\n"
            default:
                fail("Unexpected sh call: ${args}")
            }
        })

        // Override bindings as required for a specific test.
        extraBinding.each { key, value ->
            binding.setVariable(key, value)
        }

        GroovyShell shell = new GroovyShell(binding)
        return shell.parse(new File('vars/dockerBuildArgs.groovy'))
    }

    private static int occurrences(String value, String fragment) {
        int count = 0
        int position = 0

        while ((position = value.indexOf(fragment, position)) >= 0) {
            count++
            position += fragment.length()
        }

        return count
    }

    @Test
    void 'call() passes trimmed build agent UID using legacy and new arguments exactly once'() {
        Script script = loadScriptWithMocks()

        String result = script.call([
            cachebust: false
        ])

        String legacyArgument = " --build-arg UID=${BUILD_AGENT_UID_MOCK}"
        String newArgument =
            " --build-arg DAOS_SERVER_UID=${BUILD_AGENT_UID_MOCK}"

        assertEquals(1, occurrences(result, legacyArgument))
        assertEquals(1, occurrences(result, newArgument))
        assertFalse(result.contains("${BUILD_AGENT_UID_MOCK}\n"))
    }

    @Test
    void 'call() passes basic build arguments'() {
        Script script = loadScriptWithMocks()

        String result = script.call([
            cachebust: false
        ])

        assertTrue(result.contains(' --build-arg NOBUILD=1'))
        assertTrue(
            result.contains(" --build-arg JENKINS_URL=${JENKINS_URL_MOCK}")
        )
        assertTrue(result.endsWith(' '))
    }

    @Test
    void 'call() adds cachebust arguments by default'() {
        Script script = loadScriptWithMocks()

        String result = script.call()

        assertTrue(
            result.contains(" --build-arg CACHEBUST=${BUILD_START_TIME_MOCK}")
        )
        assertTrue(result.contains(' --build-arg CB0='))
    }

    @Test
    void 'call() does not add cachebust arguments when disabled'() {
        Script script = loadScriptWithMocks()

        String result = script.call([
            cachebust: false
        ])

        assertFalse(result.contains('--build-arg CACHEBUST='))
        assertFalse(result.contains('--build-arg CB0='))
    }

    @Test
    void 'call() passes supported environment variables'() {
        Script script = loadScriptWithMocks([
            env: [
                JENKINS_URL         : JENKINS_URL_MOCK,
                STAGE_NAME          : 'Build',
                DAOS_LAB_CA_FILE_URL: 'https://example.com/ca.pem',
                REPO_FILE_URL       : 'https://example.com/repo.list',
                HTTP_PROXY          : 'http://proxy.example.com:8080'
            ]
        ])

        String result = script.call([
            cachebust: false
        ])

        assertTrue(
            result.contains(
                ' --build-arg DAOS_LAB_CA_FILE_URL="https://example.com/ca.pem"'
            )
        )
        assertTrue(
            result.contains(
                ' --build-arg REPO_FILE_URL="https://example.com/repo.list"'
            )
        )
        assertTrue(
            result.contains(
                ' --build-arg HTTP_PROXY="http://proxy.example.com:8080"'
            )
        )
    }

    @Test
    void 'call() does not pass optional environment variables when absent'() {
        Script script = loadScriptWithMocks()

        String result = script.call([
            cachebust: false
        ])

        assertFalse(result.contains('--build-arg DAOS_LAB_CA_FILE_URL='))
        assertFalse(result.contains('--build-arg REPO_FILE_URL='))
        assertFalse(result.contains('--build-arg HTTP_PROXY='))
    }

    @Test
    void 'call() sets quick build arguments'() {
        Script script = loadScriptWithMocks()

        String result = script.call([
            cachebust: false,
            qb       : true
        ])

        assertTrue(result.contains(' --build-arg QUICKBUILD=true'))
        assertTrue(result.contains(' --build-arg DAOS_DEPS_BUILD=no'))
    }

    @Test
    void 'call() does not enable quick build by default'() {
        Script script = loadScriptWithMocks()

        String result = script.call([
            cachebust: false
        ])

        assertFalse(result.contains('--build-arg QUICKBUILD=true'))
    }

    @Test
    void 'call() enables dependency build'() {
        Script script = loadScriptWithMocks()

        String result = script.call([
            cachebust : false,
            deps_build: true
        ])

        assertTrue(result.contains(' --build-arg DAOS_DEPS_BUILD=yes'))
        assertTrue(result.contains(' --build-arg DAOS_BUILD=no'))
        assertFalse(result.contains('--build-arg QUICKBUILD=true'))
    }

    @Test
    void 'call() disables dependency build by default'() {
        Script script = loadScriptWithMocks()

        String result = script.call([
            cachebust: false
        ])

        assertTrue(result.contains(' --build-arg DAOS_DEPS_BUILD=no'))
        assertFalse(result.contains('--build-arg DAOS_BUILD=no'))
    }

    @Test
    void 'call() sets number of parallel dependency jobs'() {
        List<Map> shCalls = []
        Script script = loadScriptWithMocks([:], shCalls)

        String result = script.call([
            cachebust    : false,
            parallel_build: true
        ])

        assertTrue(
            result.contains(" --build-arg DEPS_JOBS=${PROCESSOR_COUNT_MOCK}")
        )
        assertEquals(
            1,
            shCalls.count {
                it.label == 'Get number of processors online'
            }
        )
    }

    @Test
    void 'call() does not calculate dependency jobs when parallel build is disabled'() {
        List<Map> shCalls = []
        Script script = loadScriptWithMocks([:], shCalls)

        String result = script.call([
            cachebust    : false,
            parallel_build: false
        ])

        assertFalse(result.contains('--build-arg DEPS_JOBS='))
        assertEquals(
            0,
            shCalls.count {
                it.label == 'Get number of processors online'
            }
        )
    }

    @Test
    void 'call() passes DAOS proxy arguments'() {
        String daosNoProxy = 'localhost,127.0.0.1'
        String daosHttpsProxy = 'http://proxy.example.com:8080'

        Script script = loadScriptWithMocks([
            env: [
                JENKINS_URL     : JENKINS_URL_MOCK,
                STAGE_NAME      : 'Build',
                DAOS_NO_PROXY   : daosNoProxy,
                DAOS_HTTPS_PROXY: daosHttpsProxy
            ],
            DAOS_NO_PROXY   : daosNoProxy,
            DAOS_HTTPS_PROXY: daosHttpsProxy
        ])

        String result = script.call([
            cachebust: false
        ])

        assertTrue(
            result.contains(" --build-arg DAOS_NO_PROXY=\"${daosNoProxy}\"")
        )
        assertTrue(
            result.contains(" --build-arg HTTPS_PROXY=\"${daosHttpsProxy}\"")
        )
        assertTrue(
            result.contains(
                " --build-arg DAOS_HTTPS_PROXY=\"${daosHttpsProxy}\""
            )
        )
    }

    @Test
    void 'call() does not pass DAOS proxy arguments when absent'() {
        Script script = loadScriptWithMocks()

        String result = script.call([
            cachebust: false
        ])

        assertFalse(result.contains('--build-arg DAOS_NO_PROXY='))
        assertFalse(result.contains('--build-arg HTTPS_PROXY='))
        assertFalse(result.contains('--build-arg DAOS_HTTPS_PROXY='))
    }

    @Test
    void 'call() does not pass DAOS proxy arguments for fault injection stage'() {
        Script script = loadScriptWithMocks([
            env: [
                JENKINS_URL     : JENKINS_URL_MOCK,
                STAGE_NAME      : 'Fault injection test',
                DAOS_NO_PROXY   : 'localhost',
                DAOS_HTTPS_PROXY: 'http://proxy.example.com:8080'
            ],
            DAOS_NO_PROXY   : 'localhost',
            DAOS_HTTPS_PROXY: 'http://proxy.example.com:8080'
        ])

        String result = script.call([
            cachebust: false
        ])

        assertFalse(result.contains('--build-arg DAOS_NO_PROXY='))
        assertFalse(result.contains('--build-arg HTTPS_PROXY='))
        assertFalse(result.contains('--build-arg DAOS_HTTPS_PROXY='))
    }
}
