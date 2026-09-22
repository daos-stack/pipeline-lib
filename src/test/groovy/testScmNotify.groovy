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

    static final String CALLER_CREDENTIALS_MOCK = 'caller-credentials'
    static final String SYSTEM_CREDENTIALS_MOCK = 'system-credentials'
    static final String FALLBACK_CREDENTIALS_MOCK = 'fallback-credentials'

    private List<String> logMessages
    private List<Map> notifyCalls
    private List<Map> sleepCalls

    static private Map defaultSleep = [time: 5, unit: 'SECONDS']

    @BeforeEach
    void setUp() {
        logMessages = []
        notifyCalls = []
        sleepCalls = []
    }

    private Script loadScriptWithMocks(Map extraBinding = [:]) {
        Binding binding = new Binding()

        // ---- ENV ----
        binding.setVariable('env', [
            DAOS_JENKINS_NOTIFY_STATUS: FALLBACK_CREDENTIALS_MOCK
        ])

        /*
         * Script.println() writes to the 'out' binding variable.
         * Defining a 'println' closure in Binding is not sufficient,
         * because groovy.lang.Script already provides println().
         */
        PrintWriter output = new PrintWriter(System.out) {

            @Override
            void println(String message) {
                logMessages << message
            }
        }

        binding.setVariable('out', output)

        binding.setVariable('echo', { String message ->
            logMessages << message
        })

        binding.setVariable('sleep', { Map config ->
            sleepCalls << new LinkedHashMap(config)
        })

        /*
         * Jenkins retry executes the body again whenever it throws.
         * Interruption exceptions must be propagated immediately because
         * they represent an aborted build rather than a retryable failure.
         */
        binding.setVariable('retry', { Integer attempts, Closure body ->
            Exception lastFailure = null

            for (int attempt = 0; attempt < attempts; attempt++) {
                try {
                    return body.call()
                } catch (InterruptedException interruption) {
                    throw interruption
                } catch (Exception failure) {
                    lastFailure = failure
                }
            }

            throw lastFailure
        })

        // ---- INTERNAL LIBRARY STEPS ----
        binding.setVariable('scmStatusIdSystem', {
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
            fail('scmNotifyTrusted() should not be called')
        }

        Script script = loadScriptWithMocks([
            env: [
                DAOS_JENKINS_NOTIFY_STATUS: null
            ],
            scmNotifyTrusted: scmNotifyTrusted
        ])

        script.call([:])

        assertTrue(
            logMessages.contains(script.NO_NOTIFY_MSG),
            "Expected message was not logged. Actual messages: ${logMessages}"
        )
    }

    @Test
    void 'call() preserves credentialsId provided by caller'() {
        Closure scmStatusIdSystem = {
            fail('scmStatusIdSystem() should not be called')
        }

        Script script = loadScriptWithMocks([
            scmStatusIdSystem: scmStatusIdSystem
        ])

        Map config = [
            credentialsId: CALLER_CREDENTIALS_MOCK
        ]
        script.call(config)

        assertEquals(1, notifyCalls.size())
        assertEquals(config, notifyCalls.first())
    }

    @Test
    void 'call() uses credentials returned by scmStatusIdSystem()'() {
        Closure scmStatusIdSystem = {
            return SYSTEM_CREDENTIALS_MOCK
        }

        Script script = loadScriptWithMocks([
            scmStatusIdSystem: scmStatusIdSystem
        ])

        script.call([:])

        assertEquals(1, notifyCalls.size())
        assertEquals(
            SYSTEM_CREDENTIALS_MOCK,
            notifyCalls.first().credentialsId
        )
    }

    @Test
    void 'call() falls back to environment credentials when scmStatusIdSystem() is missing'() {
        Closure scmStatusIdSystem = {
            throw new NoSuchMethodError('scmStatusIdSystem')
        }

        Script script = loadScriptWithMocks([
            scmStatusIdSystem: scmStatusIdSystem
        ])

        script.call([:])

        assertEquals(1, notifyCalls.size())
        assertEquals(
            FALLBACK_CREDENTIALS_MOCK,
            notifyCalls.first().credentialsId
        )
    }

    @Test
    void 'call() retries notification three times'() {
        String failMsg = 'Temporary failure'

        List<RuntimeException> failures = [
            new RuntimeException(failMsg),
            new RuntimeException(failMsg)
        ]

        Closure scmNotifyTrusted = { Map config ->
            if (failures) {
                throw failures.remove(0)
            }

            notifyCalls << new LinkedHashMap(config)
        }

        Script script = loadScriptWithMocks([
            scmNotifyTrusted: scmNotifyTrusted
        ])

        script.call([:])

        assertTrue(failures.empty)
        assertEquals(2, sleepCalls.size())
        assertEquals(defaultSleep, sleepCalls[0])
        assertEquals(defaultSleep, sleepCalls[1])
        assertEquals(1, notifyCalls.size())

        List<String> expected = [
            'WARNING: GitHub notification attempt',
            '1/3', '2/3', failMsg
        ]

        assertTrue( expected.every {
            logMessages.join().contains(it) },
            "Not all expected substrings (${expected}) found.\n" +
            "Actual messages: ${logMessages}"
        )

        assertFalse( logMessages.any {
                it.startsWith('ERROR: could not notify GitHub')
            },
            'Unexpected final error was logged. ' +
                "Actual messages: ${logMessages}"
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
        script.call([:])

        assertEquals(2, sleepCalls.size())
        assertEquals(defaultSleep, sleepCalls[0])
        assertEquals(defaultSleep, sleepCalls[1])

        List expected = [
            'WARNING: GitHub notification attempt',
            '1/3', '2/3', '3/3',
            'ERROR: could not notify GitHub'
        ]

        assertTrue( expected.every {
            logMessages.join().contains(it) },
                "Not all expected substrings (${expected}) found.\n" +
                "Actual messages: ${logMessages}"
        )
    }

    @Test
    void 'call() rethrows InterruptedException'() {
        InterruptedException interruption =
            new InterruptedException('Build canceled during notification')

        Closure scmNotifyTrusted = { Map config ->
            throw interruption
        }

        Script script = loadScriptWithMocks([
            scmNotifyTrusted: scmNotifyTrusted
        ])

        InterruptedException thrown = assertThrows(
            InterruptedException
        ) {
            script.call([:])
        }

        assertSame(interruption, thrown)
        assertTrue(sleepCalls.isEmpty())
        assertFalse(
            logMessages.any {
                it.startsWith('WARNING:') || it.startsWith('ERROR:')
            },
            "Interruption should not be logged as a notification failure. " +
                "Actual messages: ${logMessages}"
        )
    }

    @Test
    void 'call() rethrows InterruptedException raised during sleep'() {
        InterruptedException interruption =
            new InterruptedException('Build canceled during sleep')

        int notifyAttempts = 0

        Closure scmNotifyTrusted = { Map config ->
            notifyAttempts++
            throw new RuntimeException('Temporary failure')
        }

        Closure sleep = { Map config ->
            sleepCalls << new LinkedHashMap(config)
            throw interruption
        }

        Script script = loadScriptWithMocks([
            scmNotifyTrusted: scmNotifyTrusted,
            sleep           : sleep
        ])

        InterruptedException thrown = assertThrows(
            InterruptedException
        ) {
            script.call([:])
        }

        assertSame(interruption, thrown)
        assertEquals(1, notifyAttempts)
        assertEquals(1, sleepCalls.size())
        assertEquals(defaultSleep, sleepCalls.first())
        assertFalse(
            logMessages.any {
                it.startsWith('ERROR: could not notify GitHub')
            },
            "Interruption should not be treated as a non-fatal error. " +
                "Actual messages: ${logMessages}"
        )
    }

    @Test
    void 'call() passes configuration to scmNotifyTrusted()'() {
        Map capturedConfig = null

        Closure scmNotifyTrusted = { Map config ->
            capturedConfig = new LinkedHashMap(config)
            notifyCalls << new LinkedHashMap(config)
        }

        Map originalConfig = [
            description: 'Unit tests passed',
            status     : 'SUCCESS'
        ]

        Script script = loadScriptWithMocks([
            scmNotifyTrusted: scmNotifyTrusted
        ])

        script.call(originalConfig)

        assertEquals(1, notifyCalls.size())
        assertEquals(originalConfig, capturedConfig)
    }

    @Test
    void 'call() notifies SCM once without sleeping when first attempt succeeds'() {
        Script script = loadScriptWithMocks()

        script.call([:])

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
