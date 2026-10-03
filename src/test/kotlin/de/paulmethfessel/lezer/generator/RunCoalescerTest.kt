package de.paulmethfessel.lezer.generator

import de.paulmethfessel.lezer.generator.run.RunCoalescer
import junit.framework.TestCase

class RunCoalescerTest : TestCase() {
    fun testRunsOnceWhenIdle() {
        val runs = RunCoalescer<String>()
        assertTrue(runs.request("a"))
        assertFalse(runs.finished("a"))
        // Idle again
        assertTrue(runs.request("a"))
    }

    fun testRequestsWhileRunningRerunOnce() {
        val runs = RunCoalescer<String>()
        assertTrue(runs.request("a"))
        assertFalse(runs.request("a"))
        assertFalse(runs.request("a"))
        assertTrue("the last request must be handled", runs.finished("a"))
        assertFalse(runs.finished("a"))
        assertTrue(runs.request("a"))
    }

    fun testKeysAreIndependent() {
        val runs = RunCoalescer<String>()
        assertTrue(runs.request("a"))
        assertTrue(runs.request("b"))
        assertFalse(runs.finished("b"))
        assertFalse(runs.request("a"))
    }
}
