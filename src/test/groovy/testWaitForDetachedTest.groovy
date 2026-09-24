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

class TestWaitForDetachedTest {

    static final String POLL = 'poll.sh'
    static final String KILL = 'kill.sh'

    List<String> scripts = []
    List<Integer> sleeps = []

    private Script loadScript(List statuses, Map extraBinding = [:]) {
        Binding binding = new Binding()
        commonBindings(binding)
        binding.setVariable('env', [STAGE_NAME: 'stage'])

        Iterator it = statuses.iterator()
        binding.setVariable('sh', { Map m ->
            scripts << m.script
            if (m.script == POLL) {
                def next = it.next()
                if (next instanceof Throwable) {
                    throw next
                }
                return next
            }
            return 0
        })
        binding.setVariable('sleep', { Map m -> sleeps << m.time })
        binding.setVariable('fileExists', { String f -> true })
        binding.setVariable('readFile', { String f -> '7\n' })

        extraBinding.each { k, v ->
            binding.setVariable(k, v)
        }

        GroovyShell shell = new GroovyShell(binding)
        return shell.parse(new File('vars/waitForDetachedTest.groovy'))
    }

    @Test
    void 'polls until finished and returns the run exit status'() {
        Script script = loadScript([3, 3, 0])

        int rc = script.call(script: POLL, kill_script: KILL, interval: 5)

        assertEquals(7, rc)
        assertEquals([POLL, POLL, POLL], scripts)
        assertEquals([5, 5], sleeps)
    }

    @Test
    void 'returns zero when no detached run was launched'() {
        Script script = loadScript([5])

        assertEquals(0, script.call(script: POLL, kill_script: KILL))
        assertEquals([], sleeps)
    }

    @Test
    void 'transient failures are retried and reset by a successful poll'() {
        Script script = loadScript([4, new IOException('channel closed'), 3, 4, 0])

        int rc = script.call(script: POLL, kill_script: KILL, max_failures: 3)

        assertEquals(7, rc)
        assertFalse(scripts.contains(KILL))
    }

    @Test
    void 'gives up and stops the run after too many failed polls'() {
        Script script = loadScript([4, 4, 4])

        int rc = script.call(script: POLL, kill_script: KILL, max_failures: 3)

        assertEquals(255, rc)
        assertEquals(KILL, scripts.last())
    }

    @Test
    void 'stops the run when it exceeds the timeout'() {
        Script script = loadScript([3])

        int rc = script.call(script: POLL, kill_script: KILL, timeout_hours: -1)

        assertEquals(124, rc)
        assertEquals(KILL, scripts.last())
    }

    @Test
    void 'stops the run and rethrows when the build is aborted'() {
        Script script = loadScript([3], [
            sleep: { Map m -> throw new InterruptedException('aborted') }
        ])

        assertThrows(InterruptedException) {
            script.call(script: POLL, kill_script: KILL)
        }
        assertEquals(KILL, scripts.last())
    }

    @Test
    void 'returns failure when the finished run left no exit status'() {
        Script script = loadScript([0], [fileExists: { String f -> false }])

        assertEquals(255, script.call(script: POLL, kill_script: KILL))
    }
}
