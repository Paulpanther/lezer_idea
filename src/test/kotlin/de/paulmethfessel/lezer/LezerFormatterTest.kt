package de.paulmethfessel.lezer

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

class LezerFormatterTest : BasePlatformTestCase() {
    override fun getTestDataPath(): String = "src/test/testData/formatter"

    fun testSpacing() = doTest()

    fun testIndentation() = doTest()

    fun testComments() = doTest()

    /**
     * Real-world grammars only differ from the formatter's style in details, so check that formatting them only
     * changes whitespace and is stable.
     */
    fun testRealGrammars() {
        val files = File("src/test/testData/parser/real").listFiles { f -> f.extension == "grammar" }!!.sortedBy { it.name }
        assertTrue(files.isNotEmpty())
        for (file in files) {
            val text = file.readText()
            val formatted = format(file.name, text)
            assertEquals(file.name, text.filterNot { it.isWhitespace() }, formatted.filterNot { it.isWhitespace() })
            assertEquals(file.name, formatted, format(file.name, formatted))
        }
    }

    private fun doTest() {
        val name = getTestName(false)
        myFixture.configureByFile("$name.grammar")
        WriteCommandAction.runWriteCommandAction(project) {
            CodeStyleManager.getInstance(project).reformat(myFixture.file)
        }
        myFixture.checkResultByFile("$name.after.grammar")
    }

    private fun format(name: String, text: String): String {
        val file = myFixture.configureByText(name, text)
        WriteCommandAction.runWriteCommandAction(project) {
            CodeStyleManager.getInstance(project).reformat(file)
        }
        return myFixture.editor.document.text
    }
}
