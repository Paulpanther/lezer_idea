package de.paulmethfessel.lezer.playground

import com.google.gson.Gson
import junit.framework.TestCase
import kotlin.io.path.Path

class PlaygroundUnitTest : TestCase() {
    fun testDeepestAt() {
        val tree = Gson().fromJson(
            """{"n":"P","f":0,"t":7,"c":[{"n":"Call","f":0,"t":4,"c":[{"n":"Name","f":0,"t":1},{"n":"Args","f":1,"t":4}]},{"n":"Number","f":5,"t":7}]}""",
            PlaygroundNode::class.java,
        )
        assertEquals(listOf("P", "Call", "Name"), tree.deepestAt(0).map { it.name })
        // At the boundary of two nodes the one that starts there wins
        assertEquals(listOf("P", "Call", "Args"), tree.deepestAt(1).map { it.name })
        // At the end of the last node, it is still selected
        assertEquals(listOf("P", "Number"), tree.deepestAt(7).map { it.name })
        assertEquals(listOf("P"), tree.deepestAt(4).map { it.name }.take(1))
        assertEquals(emptyList<String>(), tree.deepestAt(8).map { it.name })
        assertEquals(5, tree.walk().count())
    }

    fun testResultJson() {
        val result = PlaygroundResult.parse(
            Gson(),
            """{"id":3,"tree":{"n":"P","f":0,"t":1,"c":[{"n":"⚠","f":1,"t":1,"e":true}]},"moduleErrors":["x"],"truncated":false,"ms":5}""",
        )
        assertEquals(3L, result.id)
        assertTrue(result.tree!!.childList.single().isError)
        assertEquals(listOf("x"), result.moduleErrors)
    }

    fun testResultJsonWithMissingProperties() {
        val result = PlaygroundResult.parse(Gson(), """{"id":1}""")
        assertEquals(1L, result.id)
        assertNull(result.tree)
        assertEquals(emptyList<String>(), result.moduleErrors)
        assertFalse(result.truncated)
    }

    fun testModuleChangesThatRestartTheWorker() {
        val loaded = setOf(Path("/p/src/tokens.js"), Path("/p/node_modules/dep/index.js"))
        val outputs = setOf(Path("/p/src/parser.js"), Path("/p/src/parser.terms.js"))
        val restarts = { file: String -> PlaygroundModuleListener.needsRestart(Path(file), loaded, outputs) }
        assertTrue(restarts("/p/src/tokens.js"))
        // Could be imported by tokens.js
        assertTrue(restarts("/p/src/util.ts"))
        assertTrue(restarts("/p/node_modules/dep/index.js"))
        assertFalse(restarts("/p/node_modules/other/index.js"))
        assertFalse(restarts("/p/src/parser.js"))
        assertFalse(restarts("/p/src/parser.terms.js"))
    }
}
