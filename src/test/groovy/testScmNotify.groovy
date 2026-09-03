/*
 * Copyright 2026 Hewlett Packard Enterprise Development LP
 *
 * SPDX-License-Identifier: BSD-2-Clause-Patent
 */

import static helpers.Bindings.*
import static org.junit.jupiter.api.Assertions.*

import groovy.lang.Binding
import groovy.lang.GroovyShell
import java.io.PrintWriter
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TestScmNotify {

    static final String CONTEXT_MOCK = 'unit-test'
    static final String CALLER_CREDENTIALS_MOCK = 'caller-credentials'
    static final String SYSTEM_CREDENTIALS_MOCK = 'system-credentials'
    static final String FALLBACK_CREDENTIALS_MOCK = 'fallback-credentials'

    private List<String> logMessages
    private List<Map> notifyCalls
    private List<Map> sleepCalls

    private int retryCount
    private int statusIdCalls

    @BeforeEach
    void setUp() {
        logMessages = []
        notifyCalls = []
        sleepCalls = []

        retryCount = 0
        statusIdCalls = 0
    }

    private Script loadScriptWithMocks(Map extraBinding = [:]) {
        Binding binding = new Binding()

        // ---- ENV ----
        binding.setVariable('env', [
            DAOS_JENKINS_NOTIFY_STATUS: FALLBACK_CREDENTIALS_MOCK
        ])

        // ---- PIPELINE STEP MOCKS ----
        commonBindings(binding)

        /*
         * Script.println() writes to the "out" binding variable.
         * Defining a "println" closure in Binding is not sufficient,
         * because groovy.lang.Script already provides println().
         */
        PrintWriter output = new PrintWriter(System.out) {

            @Override
            void println(Object message) {
                logMessages << String.valueOf(message)
            }

            @Override
            void println(String message) {
                logMessages << message
            }
        }

        binding.setVariable('out', output)

        /*
         * Override echo from commonBindings() so messages can be verified.
         */
        binding.setVariable('echo', { String message ->
            logMessages << message
        })

        binding.setVariable('sleep', { Map config ->
            sleepCalls << new LinkedHashMap(config)
        })

        /*
         * Jenkins retry executes the body again whenever it throws.
         * This mock reproduces that behavior.
         */
        binding.setVariable('retry', { Integer attempts, Closure body ->
            Exception lastFailure = null

            for (int attempt = 1; attempt <= attempts; attempt++) {
                retryCount++

                try {
                    return body.call()
                } catch (Exception failure) {
                    lastFailure = failure
                }
            }

            throw lastFailure
        })

        // ---- INTERNAL LIBRARY STEPS ----
        binding.setVariable('scmStatusIdSystem', {
            statusIdCalls++
            return SYSTEM_CREDENTIALS_MOCK
        })

        binding.setVariable('scmNotifyTrusted', { Map config ->
            notifyCalls << new LinkedHashMap(config)
        })

        // Override bindings as required for a specific test.
        extraBinding.each { key, value ->
            binding.setVariable(key, value)
        }

        GroovyShell shell = new GroovyShell(binding)
        return shell.parse(new File('vars/scmNotify.groovy'))
    }

    @Test
    void 'call() does nothing when SCM notification is not configured'() {
        Closure scmNotifyTrusted = { Map config ->
            notifyCalls << new LinkedHashMap(config)
        }

        Script script = loadScriptWithMocks([
            env: [
                DAOS_JENKINS_NOTIFY_STATUS: null
            ],
            scmNotifyTrusted: scmNotifyTrusted
        ])

        script.call([
            context: CONTEXT_MOCK
        ])

        assertTrue(notifyCalls.isEmpty())
        assertEquals(0, retryCount)
        assertTrue(
            logMessages.contains(
                'Jenkins not configured to notify SCM repository of builds.'
            ),
            "Expected message was not logged. Actual messages: ${logMessages}"
        )
    }

    @Test
    void 'call() preserves credentialsId provided by caller'() {
        Closure scmStatusIdSystem = {
            statusIdCalls++
            return SYSTEM_CREDENTIALS_MOCK
        }

        Script script = loadScriptWithMocks([
            scmStatusIdSystem: scmStatusIdSystem
        ])

        script.call([
            credentialsId: CALLER_CREDENTIALS_MOCK,
            context      : CONTEXT_MOCK
        ])

        assertEquals(0, statusIdCalls)
        assertEquals(1, notifyCalls.size())
        assertEquals(
            CALLER_CREDENTIALS_MOCK,
            notifyCalls.first().credentialsId
        )
        assertEquals(
            CONTEXT_MOCK,
            notifyCalls.first().context
        )
    }

    @Test
    void 'call() uses credentials returned by scmStatusIdSystem()'() {
        Closure scmStatusIdSystem = {
            statusIdCalls++
            return SYSTEM_CREDENTIALS_MOCK
        }

        Script script = loadScriptWithMocks([
            scmStatusIdSystem: scmStatusIdSystem
        ])

        script.call([
            context: CONTEXT_MOCK
        ])

        assertEquals(1, statusIdCalls)
        assertEquals(1, notifyCalls.size())
        assertEquals(
            SYSTEM_CREDENTIALS_MOCK,
            notifyCalls.first().credentialsId
        )
    }

    @Test
    void 'call() falls back to environment credentials when scmStatusIdSystem() is missing'() {
        Closure scmStatusIdSystem = {
            statusIdCalls++
            throw new NoSuchMethodError('scmStatusIdSystem')
        }

        Script script = loadScriptWithMocks([
            scmStatusIdSystem: scmStatusIdSystem
        ])

        script.call([
            context: CONTEXT_MOCK
        ])

        assertEquals(1, statusIdCalls)
        assertEquals(1, notifyCalls.size())
        assertEquals(
            FALLBACK_CREDENTIALS_MOCK,
            notifyCalls.first().credentialsId
        )
    }

    @Test
    void 'call() retries notification three times'() {
        int notifyAttempts = 0

        Closure scmNotifyTrusted = { Map config ->
            notifyAttempts++

            if (notifyAttempts < 3) {
                throw new RuntimeException(
                    "Temporary failure ${notifyAttempts}"
                )
            }

            notifyCalls << new LinkedHashMap(config)
        }

        Script script = loadScriptWithMocks([
            scmNotifyTrusted: scmNotifyTrusted
        ])

        script.call([
            context: CONTEXT_MOCK
        ])

        assertEquals(3, notifyAttempts)
        assertEquals(3, retryCount)

        assertEquals(2, sleepCalls.size())
        assertEquals(
            [time: 5, unit: 'SECONDS'],
            sleepCalls[0]
        )
        assertEquals(
            [time: 5, unit: 'SECONDS'],
            sleepCalls[1]
        )

        assertEquals(1, notifyCalls.size())
        assertEquals(
            CONTEXT_MOCK,
            notifyCalls.first().context
        )

        assertTrue(
            logMessages.contains(
                'WARNING: GitHub notification attempt 1/3 failed ' +
                    '(Temporary failure 1).'
            ),
            "First warning was not logged. Actual messages: ${logMessages}"
        )
        assertTrue(
            logMessages.contains(
                'WARNING: GitHub notification attempt 2/3 failed ' +
                    '(Temporary failure 2).'
            ),
            "Second warning was not logged. Actual messages: ${logMessages}"
        )
        assertFalse(
            logMessages.any { message ->
                message.startsWith('ERROR: could not notify GitHub')
            },
            "Unexpected final error was logged. Actual messages: ${logMessages}"
        )
    }

    @Test
    void 'call() logs each failure and continues after final failure'() {
        Closure scmNotifyTrusted = { Map config ->
            throw new RuntimeException('GitHub unavailable')
        }

        Script script = loadScriptWithMocks([
            scmNotifyTrusted: scmNotifyTrusted
        ])

        /*
         * An unhandled exception automatically fails this test.
         * A successful return confirms that notification failures are non-fatal.
         */
        script.call([
            context: CONTEXT_MOCK
        ])

        assertEquals(3, retryCount)
        assertEquals(2, sleepCalls.size())
        assertEquals(
            [time: 5, unit: 'SECONDS'],
            sleepCalls[0]
        )
        assertEquals(
            [time: 5, unit: 'SECONDS'],
            sleepCalls[1]
        )

        assertTrue(
            logMessages.contains(
                'WARNING: GitHub notification attempt 1/3 failed ' +
                    '(GitHub unavailable).'
            ),
            "First warning was not logged. Actual messages: ${logMessages}"
        )
        assertTrue(
            logMessages.contains(
                'WARNING: GitHub notification attempt 2/3 failed ' +
                    '(GitHub unavailable).'
            ),
            "Second warning was not logged. Actual messages: ${logMessages}"
        )
        assertTrue(
            logMessages.contains(
                'WARNING: GitHub notification attempt 3/3 failed ' +
                    '(GitHub unavailable).'
            ),
            "Third warning was not logged. Actual messages: ${logMessages}"
        )
        assertTrue(
            logMessages.contains(
                'ERROR: could not notify GitHub after 3 attempts ' +
                    '(GitHub unavailable); continuing because status ' +
                    'notification is non-fatal.'
            ),
            "Final error was not logged. Actual messages: ${logMessages}"
        )
    }

    @Test
    void 'call() passes configuration to scmNotifyTrusted()'() {
        Map capturedConfig = null

        Closure scmNotifyTrusted = { Map config ->
            capturedConfig = config
            notifyCalls << config
        }

        Map originalConfig = [
            context    : CONTEXT_MOCK,
            description: 'Unit tests passed',
            status     : 'SUCCESS'
        ]

        Script script = loadScriptWithMocks([
            scmNotifyTrusted: scmNotifyTrusted
        ])

        script.call(originalConfig)

        assertEquals(1, notifyCalls.size())
        assertSame(originalConfig, capturedConfig)
        assertEquals(
            CONTEXT_MOCK,
            capturedConfig.context
        )
        assertEquals(
            'Unit tests passed',
            capturedConfig.description
        )
        assertEquals(
            'SUCCESS',
            capturedConfig.status
        )
        assertEquals(
            SYSTEM_CREDENTIALS_MOCK,
            capturedConfig.credentialsId
        )
    }

    @Test
    void 'call() notifies SCM once without sleeping when first attempt succeeds'() {
        Script script = loadScriptWithMocks()

        script.call([
            context: CONTEXT_MOCK
        ])

        assertEquals(1, retryCount)
        assertEquals(1, notifyCalls.size())
        assertTrue(sleepCalls.isEmpty())
        assertFalse(
            logMessages.any { it.startsWith('WARNING:') }
        )
        assertFalse(
            logMessages.any { it.startsWith('ERROR:') }
        )
    }
}
