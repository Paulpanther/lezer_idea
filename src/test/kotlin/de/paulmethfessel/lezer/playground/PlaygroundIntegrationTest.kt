package de.paulmethfessel.lezer.playground

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.paulmethfessel.lezer.generator.GeneratorMode
import de.paulmethfessel.lezer.generator.LezerSettings
import de.paulmethfessel.lezer.generator.NodeInstallation
import de.paulmethfessel.lezer.generator.NodeLocator
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import javax.swing.tree.DefaultMutableTreeNode
import kotlin.io.path.Path
import kotlin.io.path.writeText

/** Runs the real worker, needs node and `LEZER_GENERATOR_DIR` (see the README). */
class PlaygroundIntegrationTest : BasePlatformTestCase() {
    private val generatorDir: String? = System.getenv("LEZER_GENERATOR_DIR")
    private lateinit var node: NodeInstallation

    private val grammar = """
        @top Program { (Number | Word)* }
        @skip { space }
        @external propSource highlighting from "./highlight"
        @tokens { Number { @digit+ } Word { @asciiLetter+ } space { " "+ } }
    """.trimIndent()

    override fun setUp() {
        super.setUp()
        node = NodeLocator.find(project) ?: NodeInstallation(Path("/missing"), "")
    }

    override fun tearDown() {
        try {
            LezerSettings.getInstance(project).state.apply { generator = GeneratorMode.AUTO; customGeneratorPath = null }
        } finally {
            super.tearDown()
        }
    }

    private fun available() = generatorDir != null && Files.isRegularFile(node.node)

    fun testParseWithMissingModule() {
        if (!available()) return
        val result = parse(Files.createTempDirectory("lezer"), grammar, "12 ab")
        assertNull(result.grammarError)
        // The module doesn't exist, the grammar is still parsed
        assertTrue(result.moduleErrors.single(), result.moduleErrors.single().contains("./highlight"))
        assertEquals("Program[Number 0-2, Word 3-5]", describe(result.tree!!))
    }

    fun testGrammarError() {
        if (!available()) return
        val result = parse(Files.createTempDirectory("lezer"), "@top P { x }", "")
        assertEquals("Reference to undefined rule 'x' (1:9)", result.grammarError)
    }

    fun testExternalFromGrammarModule() {
        if (!available()) return
        val dir = Files.createTempDirectory("lezer")
        dir.resolve("highlight.js").writeText("export const highlighting = () => null")
        val result = parse(dir, grammar, "12 ab")
        assertNull(result.grammarError)
        assertEquals(emptyList<String>(), result.moduleErrors)
    }

    fun testPanel() {
        if (!available()) return
        LezerSettings.getInstance(project).state.apply { generator = GeneratorMode.CUSTOM; customGeneratorPath = generatorDir }
        val file = myFixture.configureByText("test.grammar", "@top Program { (Number | Word)* }\n@skip { space }\n@tokens { Number { @digit+ } Word { @asciiLetter+ } space { \" \"+ } }")
        val panel = LezerPlaygroundPanel(project)
        Disposer.register(testRootDisposable, panel)
        panel.showGrammar(file.virtualFile)
        WriteCommandAction.runWriteCommandAction(project) { panel.inputEditor.document.setText("12 ab") }
        waitForTree(panel) { it.tree?.childList?.size == 2 }

        val root = panel.tree.model.root as DefaultMutableTreeNode
        assertEquals("Program", (root.userObject as PlaygroundNode).name)

        // The caret selects the innermost node
        panel.inputEditor.caretModel.moveToOffset(4)
        assertEquals("Word", ((panel.tree.selectionPath!!.lastPathComponent as DefaultMutableTreeNode).userObject as PlaygroundNode).name)

        // Editing the grammar parses again
        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.setText(myFixture.editor.document.text.replace("Word { @asciiLetter+ }", "Word { @asciiLetter+ } Other { \"x\" }"))
        }
        WriteCommandAction.runWriteCommandAction(project) { panel.inputEditor.document.setText("12 ab 3") }
        waitForTree(panel) { it.tree?.childList?.size == 3 }
    }

    private fun waitForTree(panel: LezerPlaygroundPanel, condition: (PlaygroundResult) -> Boolean) {
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            panel.result?.let { if (condition(it)) return }
            Thread.sleep(20)
        }
        fail("No matching result, last: ${panel.result?.grammarError ?: panel.result?.tree?.let(::describe)}")
    }

    private fun parse(dir: Path, grammar: String, input: String): PlaygroundResult {
        val file = myFixture.configureByText("test.grammar", grammar) as de.paulmethfessel.lezer.psi.LezerFile
        val worker = PlaygroundWorker.getInstance(project)
        val request = PlaygroundRequest(
            worker.nextId(), generatorDir!!, grammar, dir.toString(), input, null, null,
            PlaygroundGrammarInfo.of(file).externals,
        )
        return worker.parse(node, request).get(30, TimeUnit.SECONDS)!!
    }

    private fun describe(node: PlaygroundNode): String {
        val children = node.childList.joinToString(", ") { if (it.childList.isEmpty()) "${it.name} ${it.from}-${it.to}" else describe(it) }
        return if (children.isEmpty()) node.name else "${node.name}[$children]"
    }
}
