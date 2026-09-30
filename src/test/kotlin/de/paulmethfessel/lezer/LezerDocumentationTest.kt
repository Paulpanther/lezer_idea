package de.paulmethfessel.lezer

import com.intellij.lang.LanguageDocumentation
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class LezerDocumentationTest : BasePlatformTestCase() {
    private val provider get() = LanguageDocumentation.INSTANCE.forLanguage(LezerLanguage)

    fun testLineCommentsBeforeRule() {
        val doc = docAtCaret(
            """
            @top Program { expression* }

            // An expression.
            // Can be **nested**.
            expression[@isGroup=Expression] { Number | "(" expression ")" }

            @tokens { Number { @digit+ } }
            @top Other { expr<caret>ession }
            """,
        )
        assertContains(doc, "An expression.", "<b>nested</b>", "Kind:", "Rule", "Node:", "None, the name is not capitalized")
        assertContains(doc, "Lezer guide: Writing a Grammar", "#writing-a-grammar")
        // The signature without the body
        assertContains(stripTags(doc), "expression[@isGroup=Expression]")
        assertFalse(stripTags(doc), stripTags(doc).contains("Number |"))
    }

    fun testBlockComment() {
        val doc = docAtCaret(
            """
            /**
             * The whole program.
             */
            @top Pro<caret>gram { "a" }
            """,
        )
        assertContains(doc, "The whole program.", "Top rule", "<code>Program</code>")
        assertFalse(doc.contains("*/"))
    }

    fun testNoDocForTrailingOrSeparatedComments() {
        val doc = docAtCaret(
            """
            // Separated by a blank line

            a { "a" } // trailing comment of a
            b<caret> { "b" }
            """,
        )
        assertFalse(doc.contains("Separated"))
        assertFalse(doc.contains("trailing"))
    }

    fun testTokenWithNameProp() {
        val doc = docAtCaret(
            """
            @top P { str<caret>ing }
            @tokens {
              // A string literal
              string[@name=String] { '"' ![\"]* '"' }
            }
            """,
        )
        assertContains(doc, "A string literal", "Token", "<code>String</code> (from <code>@name</code>)", "Declared in:", "@tokens", "#tokens")
    }

    fun testTemplateAndParameter() {
        val template = docAtCaret("@top P { comma<caret>Sep<\"a\"> }\ncommaSep<item> { item (\",\" item)* }")
        assertContains(template, "Parameterized rule", "#template-rules")
        assertContains(stripTags(template), "commaSep<item>")

        val parameter = docAtCaret("@top P { commaSep<\"a\"> }\ncommaSep<item> { it<caret>em (\",\" item)* }")
        assertContains(parameter, "Parameter", "Parameter of:", "<code>commaSep</code>")
    }

    fun testPrecedence() {
        val doc = docAtCaret(
            """
            @precedence {
              // Multiplication
              times @left,
              plus @left
            }
            @top P { e }
            e { e !ti<caret>mes "*" e | e !plus "+" e | "x" }
            """,
        )
        assertContains(doc, "Multiplication", "Precedence:", "1 of 2 (highest first), left associative", "#precedence")
    }

    fun testKeywords() {
        assertContains(docAtCaret("@to<caret>p P { \"a\" }"), "Declares a top rule", "#writing-a-grammar")
        assertContains(docAtCaret("@top P { \"a\" }\n@tok<caret>ens { a { \"a\" } }"), "Declares tokens", "#tokens")
        assertContains(docAtCaret("@prec<caret>edence { a @left }"), "named precedences", "#precedence")
        assertContains(docAtCaret("@tokens { @prec<caret>edence { a, \"b\" } }"), "Token precedence", "#token-precedence")
        assertContains(docAtCaret("@precedence { a @ri<caret>ght }"), "right-associative")
        assertContains(docAtCaret("@external to<caret>kens t from \"./t\" { A }"), "ExternalTokenizer", "#external-tokens")
        assertContains(docAtCaret("@external prop p fr<caret>om \"./p\""), "NodeProp", "#node-props")
        assertContains(docAtCaret("@tokens { a { @dig<caret>it+ } }"), "ASCII digit", "#tokens")
        assertContains(docAtCaret("@tokens { a { \"'\" <caret>_ } }"), "any single character")
        assertContains(docAtCaret("@top P { \"a\" }\nb { @spec<caret>ialize<a, \"b\"> }"), "Specializes a token", "#token-specialization")
        assertContains(docAtCaret("@top P { a <caret>~amb }\na { \"a\" }"), "Ambiguity marker", "#allowing-ambiguity")
    }

    fun testProps() {
        assertContains(docAtCaret("@top P { a }\na[@isGr<caret>oup=A] { \"a\" }"), "Adds the group", "#node-groups")
        assertContains(docAtCaret("@top P { a }\na[@dynamicPrec<caret>edence=1] { \"a\" }"), "between -10 and 10")
        assertContains(docAtCaret("@top P { a }\na[@unkn<caret>own] { \"a\" }"), "The known ones are")
        assertContains(docAtCaret("@top P { a }\nA[closed<caret>By=B] { \"a\" }"), "NodeProp.closedBy", "#node-props")
    }

    fun testNoDocForPlainNames() {
        myFixture.configureByText("test.grammar", "@top P { \"a\" }\n@external prop closedBy from \"./p\"\nA[closed<caret>By=x] { \"a\" }")
        // Resolves to the external prop, which overrides the built-in one
        val element = targetElement()
        assertContains(provider.generateDoc(element, null)!!, "External prop")
    }

    fun testQuickNavigateInfo() {
        myFixture.configureByText("test.grammar", "@top P { a<caret> }\na[@name=A] { \"a\" }")
        assertEquals("rule a[@name=A]", provider.getQuickNavigateInfo(targetElement(), null))
    }

    private fun docAtCaret(text: String): String {
        myFixture.configureByText("test.grammar", text.trimIndent())
        return provider.generateDoc(targetElement(), null) ?: error("No documentation")
    }

    /** Like the platform: a custom element for keywords, otherwise the declaration at or referenced at the caret. */
    private fun targetElement(): PsiElement {
        val context = myFixture.file.findElementAt(myFixture.caretOffset)
        return provider.getCustomDocumentationElement(myFixture.editor, myFixture.file, context, myFixture.caretOffset)
            ?: myFixture.elementAtCaret
    }

    private fun assertContains(text: String, vararg parts: String) {
        for (part in parts) assertTrue("Expected '$part' in:\n$text", text.contains(part))
    }

    private fun stripTags(html: String) = html.replace(Regex("<[^>]+>"), "").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
}
