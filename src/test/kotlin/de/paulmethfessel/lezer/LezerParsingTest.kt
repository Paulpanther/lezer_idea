package de.paulmethfessel.lezer

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.ParsingTestCase
import de.paulmethfessel.lezer.parser.LezerParserDefinition
import java.io.File

class LezerParsingTest : ParsingTestCase("", "grammar", LezerParserDefinition()) {
    override fun getTestDataPath(): String = "src/test/testData/parser"

    override fun skipSpaces(): Boolean = true

    override fun includeRanges(): Boolean = true

    fun testDeclarations() = doTest(true, true)

    fun testExpressions() = doTest(true, true)

    fun testRecovery() = doTest(true, false)

    /** Real-world grammars from the lezer-parser repositories must parse without errors. */
    fun testRealGrammars() {
        val files = File("$testDataPath/real").listFiles { f -> f.extension == "grammar" }!!.sortedBy { it.name }
        assertTrue(files.isNotEmpty())
        for (file in files) {
            val psi = createPsiFile(file.nameWithoutExtension, file.readText())
            val errors = PsiTreeUtil.findChildrenOfType(psi, PsiErrorElement::class.java)
            assertTrue(
                "${file.name}: " + errors.joinToString { "${it.errorDescription} at ${it.textOffset}" },
                errors.isEmpty(),
            )
        }
    }
}
