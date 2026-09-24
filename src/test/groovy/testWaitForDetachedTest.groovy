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

    static final String WAIT = 'wait.sh'
    static final String KILL = 'kill.sh'

    List<String> scripts = []
    List<String> labels = []
    List<Integer> sleeps = []

    private Script loadScript(List statuses, Map extraBinding = [:]) {
        Binding binding = new Binding()
        commonBindings(binding)
        binding.setVariable('env', [STAGE_NAME: 'stage'])

        Iterator it = statuses.iterator()
        binding.setVariable('sh', { Map m ->
            scripts << m.script
            labels << m.label
            if (m.script.startsWith(WAIT)) {
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
    void 'runs one wait step labelled with the stage and returns the run exit status'() {
        Script script = loadScript([0])

        int rc = script.call(script: WAIT, kill_script: KILL, interval: 5, timeout_hours: 1)

        assertEquals(7, rc)
        assertEquals(1, scripts.size())
        List<String> args = scripts[0].tokenize(' ')
        assertEquals([WAIT, '5'], args[0..1])
        long deadline = args[2] as long
        long expected = System.currentTimeMillis() / 1000 + 3600
        assertTrue(Math.abs(deadline - expected) < 60)
        assertEquals(['stage'], labels)
        assertEquals([], sleeps)
    }

    @Test
    void 'returns zero when no detached run was launched'() {
        Script script = loadScript([5])

        assertEquals(0, script.call(script: WAIT, kill_script: KILL))
        assertEquals([], sleeps)
    }

    @Test
    void 'a failed wait is started again'() {
        Script script = loadScript([4, new IOException('channel closed'), -1, 0])

        int rc = script.call(script: WAIT, kill_script: KILL, max_failures: 4, retry_interval: 9)

        assertEquals(7, rc)
        assertEquals(4, scripts.size())
        assertEquals([9, 9, 9], sleeps)
        assertFalse(scripts.contains(KILL))
    }

    @Test
    void 'gives up and stops the run after too many failed waits'() {
        Script script = loadScript([4, 4, 4])

        int rc = script.call(script: WAIT, kill_script: KILL, max_failures: 3)

        assertEquals(255, rc)
        assertEquals(KILL, scripts.last())
        assertEquals('stage (stop)', labels.last())
    }

    @Test
    void 'stops the run when the wait reports the deadline passed'() {
        Script script = loadScript([6])

        int rc = script.call(script: WAIT, kill_script: KILL)

        assertEquals(124, rc)
        assertEquals(KILL, scripts.last())
    }

    @Test
    void 'stops the run when it exceeds the timeout'() {
        Script script = loadScript([4])

        int rc = script.call(script: WAIT, kill_script: KILL, timeout_hours: -1)

        assertEquals(124, rc)
        assertEquals(KILL, scripts.last())
    }

    @Test
    void 'stops the run and rethrows when the build is aborted'() {
        Script script = loadScript([new InterruptedException('aborted')])

        assertThrows(InterruptedException) {
            script.call(script: WAIT, kill_script: KILL)
        }
        assertEquals(KILL, scripts.last())
    }

    @Test
    void 'returns failure when the finished run left no exit status'() {
        Script script = loadScript([0], [fileExists: { String f -> false }])

        assertEquals(255, script.call(script: WAIT, kill_script: KILL))
    }
}
