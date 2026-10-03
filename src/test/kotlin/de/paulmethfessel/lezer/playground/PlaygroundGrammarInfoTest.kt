package de.paulmethfessel.lezer.playground

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.paulmethfessel.lezer.psi.LezerFile
import de.paulmethfessel.lezer.psi.LezerNamedElement
import de.paulmethfessel.lezer.psi.LezerNodeNames

class PlaygroundGrammarInfoTest : BasePlatformTestCase() {
    fun testInfo() {
        val file = myFixture.configureByText(
            "test.grammar",
            """
            @dialects { ts, jsx }
            @top Script { "a" }
            @skip {} { @top Expression { "b" } }
            @external tokens insertSemi from "./tokens" { InsertSemi }
            @external prop myProp as other from "./props"
            @external propSource highlighting from "./highlight"
            @external specialize { word } spec from "./spec" { Kw }
            @context tracker from "./context.js"
            """.trimIndent(),
        ) as LezerFile
        val info = PlaygroundGrammarInfo.of(file)
        assertEquals(listOf("Script", "Expression"), info.tops)
        assertEquals(listOf("ts", "jsx"), info.dialects)
        assertEquals(
            listOf(
                ExternalDeclaration("tokenizer", "insertSemi", "./tokens"),
                ExternalDeclaration("prop", "myProp", "./props"),
                ExternalDeclaration("propSource", "highlighting", "./highlight"),
                ExternalDeclaration("specializer", "spec", "./spec"),
                ExternalDeclaration("context", "tracker", "./context.js"),
            ),
            info.externals,
        )
    }

    fun testNodeNames() {
        val file = myFixture.configureByText(
            "test.grammar",
            "@top Program { expr }\nexpr { Binary { \"x\" } | lower | named }\nlower { \"l\" }\nnamed[@name=Named] { \"n\" }",
        ) as LezerFile
        val names = PsiTreeUtil.findChildrenOfType(file, LezerNamedElement::class.java).associate { it.name to LezerNodeNames.nodeName(it) }
        assertEquals(mapOf("Program" to "Program", "expr" to null, "Binary" to "Binary", "lower" to null, "named" to "Named"), names)
        assertEquals("named", LezerNodeNames.declarationsOf(file, "Named").single().name)
        assertEquals("Binary", LezerNodeNames.declarationsOf(file, "Binary").single().name)
    }
}
