package de.paulmethfessel.lezer.features

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.paulmethfessel.lezer.javascript.LezerJsGotoDeclarationHandler
import de.paulmethfessel.lezer.javascript.SelectorNames
import de.paulmethfessel.lezer.psi.LezerNamedElement

class LezerJavaScriptTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("lang.grammar", GRAMMAR)
    }

    fun testSelectorNames() {
        assertEquals(
            listOf("String" to TextRange(1, 7), "Parent" to TextRange(8, 14), "Child" to TextRange(15, 20)),
            SelectorNames.parse("\"String Parent/Child/...\""),
        )
        assertEquals(listOf("Keyword" to TextRange(9, 16)), SelectorNames.parse("'\"(\" \")\" Keyword'"))
        assertEquals(listOf("Name" to TextRange(0, 4)), SelectorNames.parse("Name"))
        assertEquals(listOf("A" to TextRange(1, 2), "B" to TextRange(5, 6)), SelectorNames.parse("\"A/*/B!\""))
    }

    fun testGotoFromStyleTags() {
        assertEquals("String", gotoName("highlight.js", HIGHLIGHT.replace("\"String Kw", "\"Str<caret>ing Kw")))
        assertEquals("Kw", gotoName("highlight.js", HIGHLIGHT.replace("\"String Kw/", "\"String K<caret>w/")))
        assertEquals("Program", gotoName("highlight.js", HIGHLIGHT.replace("Program: t", "Prog<caret>ram: t")))
    }

    fun testNoGotoOutsideStyleTags() {
        assertNull(gotoName("other.js", "const x = {\"Str<caret>ing\": 1}"))
    }

    fun testGotoFromTermsImport() {
        assertEquals("Program", gotoName("usage.ts", USAGE.replace("import {Program", "import {Prog<caret>ram")))
        assertEquals("String", gotoName("usage.ts", USAGE.replace("String as S", "Str<caret>ing as S")))
        assertEquals("String", gotoName("usage.ts", USAGE.replace("log(S,", "log(<caret>S,")))
        assertEquals("Kw", gotoName("usage.ts", USAGE.replace("terms.Kw", "terms.K<caret>w")))
    }

    fun testFindUsages() {
        myFixture.addFileToProject("highlight.js", HIGHLIGHT)
        myFixture.addFileToProject("usage.ts", USAGE)
        val usages = myFixture.findUsages(declaration("String"))
        val inCode = usages.mapNotNull { it.element }.filter { it.containingFile.name != "lang.grammar" }
            .map { "${it.containingFile.name}:${it.text}" }
        assertSameElements(inCode, "highlight.js:\"String Kw/...\"", "usage.ts:String")
    }

    fun testRenameKeepsCode() {
        myFixture.addFileToProject("highlight.js", HIGHLIGHT)
        myFixture.addFileToProject("usage.ts", USAGE)
        myFixture.configureByFile("lang.grammar")
        myFixture.renameElement(declaration("String"), "Str")
        myFixture.checkResult(GRAMMAR.replace("String", "Str"))
        assertEquals(HIGHLIGHT, myFixture.findFileInTempDir("highlight.js").let { String(it.contentsToByteArray()) })
        assertEquals(USAGE, myFixture.findFileInTempDir("usage.ts").let { String(it.contentsToByteArray()) })
    }

    private fun declaration(name: String): LezerNamedElement {
        val file = myFixture.psiManager.findFile(myFixture.findFileInTempDir("lang.grammar"))!!
        return com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(file, LezerNamedElement::class.java).first { it.name == name }
    }

    private fun gotoName(fileName: String, text: String): String? {
        myFixture.configureByText(fileName, text)
        val offset = myFixture.caretOffset
        val targets: Array<PsiElement>? = LezerJsGotoDeclarationHandler()
            .getGotoDeclarationTargets(myFixture.file.findElementAt(offset), offset, myFixture.editor)
        return targets?.map { (it as LezerNamedElement).name }?.single()
    }

    private companion object {
        val GRAMMAR = """
            @top Program { (String | Kw)* }
            Kw { "kw" }
            @tokens { String { '"' ![\"]* '"' } }
        """.trimIndent()

        val HIGHLIGHT = """
            import {styleTags, tags as t} from "@lezer/highlight"
            export const highlighting = styleTags({
              "String Kw/...": t.string,
              '"(" ")"': t.paren,
              Program: t.content,
            })
        """.trimIndent()

        val USAGE = """
            import {Program, String as S} from "./parser.terms.js"
            import * as terms from "./parser.terms.js"
            console.log(S, Program, terms.Kw)
        """.trimIndent()
    }
}
