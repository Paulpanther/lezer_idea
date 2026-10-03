package de.paulmethfessel.lezer.playground

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class LezerPlaygroundStateTest : BasePlatformTestCase() {
    fun testRoundTrip() {
        val state = LezerPlaygroundState()
        assertEquals("", state.input("/a.grammar"))
        assertEquals(emptySet<String>(), state.dialects("/a.grammar"))

        state.setInput("/a.grammar", "1 + 2")
        state.setTop("/a.grammar", "Expression")
        state.setDialects("/a.grammar", setOf("ts", "jsx"))

        // Survives saving and loading
        val loaded = LezerPlaygroundState()
        loaded.loadState(state.state)
        assertEquals("1 + 2", loaded.input("/a.grammar"))
        assertEquals("Expression", loaded.top("/a.grammar"))
        assertEquals(setOf("ts", "jsx"), loaded.dialects("/a.grammar"))
        assertEquals("", loaded.input("/b.grammar"))

        loaded.setTop("/a.grammar", null)
        assertNull(loaded.top("/a.grammar"))
        loaded.setDialects("/a.grammar", emptySet())
        assertEquals(emptySet<String>(), loaded.dialects("/a.grammar"))
    }

    fun testModificationCount() {
        val state = LezerPlaygroundState()
        val before = state.stateModificationCount
        state.setInput("/a.grammar", "x")
        assertTrue(state.stateModificationCount > before)
        val after = state.stateModificationCount
        // Unchanged input doesn't mark the workspace as modified
        state.setInput("/a.grammar", "x")
        assertEquals(after, state.stateModificationCount)
    }
}
