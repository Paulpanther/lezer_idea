package de.paulmethfessel.lezer

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.paulmethfessel.lezer.psi.LezerDeclarationKind
import de.paulmethfessel.lezer.psi.LezerDeclarationKind.*
import de.paulmethfessel.lezer.psi.LezerNamedElement

class LezerResolveTest : BasePlatformTestCase() {
    private fun assertResolves(text: String, kind: LezerDeclarationKind, declarationText: String? = null) {
        myFixture.configureByText("test.grammar", text)
        val reference = myFixture.getReferenceAtCaretPositionWithAssertion()
        val target = reference.resolve() as? LezerNamedElement
        assertNotNull("Reference '${reference.canonicalText}' is unresolved", target)
        assertEquals(kind, target!!.kind)
        if (declarationText != null) assertEquals(declarationText, target.text)
    }

    private fun assertUnresolved(text: String) {
        myFixture.configureByText("test.grammar", text)
        assertNull(myFixture.getReferenceAtCaretPositionWithAssertion().resolve())
    }

    fun testTerm() = assertResolves("@top Program { ex<caret>pr }\nexpr { \"x\" }", TERM)

    fun testTopRule() = assertResolves("@top Program { \"x\" }\nexpr { Prog<caret>ram }", TOP_RULE)

    fun testTokenFromTerm() = assertResolves("@top Program { Num<caret>ber }\n@tokens { Number { @digit+ } }", TOKEN)

    fun testTokenFromToken() = assertResolves("@tokens { Number { dig<caret>its } digits { @digit+ } }", TOKEN)

    fun testTermNotVisibleInTokens() = assertUnresolved("expr { \"x\" }\n@tokens { Number { ex<caret>pr } }")

    fun testTemplate() = assertResolves("@top P { li<caret>st<\"x\"> }\nlist<item> { item+ }", TERM_TEMPLATE)

    fun testParameter() = assertResolves("list<item> { it<caret>em+ }", PARAMETER)

    fun testParameterShadowsRule() = assertResolves("item { \"x\" }\nlist<item> { it<caret>em+ }", PARAMETER)

    fun testParameterInPropInterpolation() =
        assertResolves("kw<term> { @specialize[@name={te<caret>rm}]<Word, term> }", PARAMETER)

    fun testParameterInSpecialize() =
        assertResolves("kw<term> { @specialize<Word, te<caret>rm> }\n@tokens { Word { @asciiLetter+ } }", PARAMETER)

    fun testSpecializedToken() =
        assertResolves("kw<term> { @specialize<Wo<caret>rd, term> }\n@tokens { Word { @asciiLetter+ } }", TOKEN)

    fun testExternalToken() =
        assertResolves("@top P { inser<caret>tSemi }\n@external tokens t from \"./t\" { insertSemi }", EXTERNAL_TOKEN)

    fun testElseToken() =
        assertResolves("@top P { cont<caret>ent }\n@local tokens { end { \"x\" } @else content }", TOKEN)

    fun testSkipScopeRule() = assertResolves("@top P { Str<caret>ing }\n@skip {} { String { \"x\" } }", TERM)

    fun testInlineRuleIsNotReferenceable() = assertUnresolved("a { Inline { \"x\" } }\nb { Inl<caret>ine }")

    fun testTokenPrecedence() =
        assertResolves("@tokens { A { \"a\" } B { \"b\" } @precedence { <caret>A, B } }", TOKEN)

    fun testPrecedenceMarker() = assertResolves("@precedence { times @left }\na { !ti<caret>mes \"x\" }", PRECEDENCE)

    fun testDialect() = assertResolves("@dialects { ts }\na[@dialect=t<caret>s] { \"x\" }", DIALECT)

    fun testExternalProp() =
        assertResolves("@external prop myProp from \"./p\"\na[my<caret>Prop=x] { \"x\" }", EXTERNAL_PROP)

    fun testExternalPropAlias() =
        assertResolves("@external prop myProp as alias from \"./p\"\na[al<caret>ias=x] { \"x\" }", EXTERNAL_PROP)

    fun testBuiltinPropIsUnresolved() = assertUnresolved("a[closed<caret>By=\")\"] { \"x\" }")

    fun testContextualKeywordAsName() = assertResolves("from { \"x\" }\na { fr<caret>om }", TERM)

    /** All non-soft references in real-world grammars from the lezer-parser repositories must resolve. */
    fun testRealGrammarsResolve() {
        val files = java.io.File("src/test/testData/parser/real").listFiles { f -> f.extension == "grammar" }!!
        for (file in files.sortedBy { it.name }) {
            val psi = myFixture.configureByText(file.name, file.readText())
            val unresolved = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(psi, com.intellij.psi.PsiElement::class.java)
                .mapNotNull { it.reference }
                .filter { !it.isSoft && it.resolve() == null }
                .map { "${it.canonicalText}@${it.element.textOffset}" }
            assertEmpty("${file.name}: unresolved references", unresolved)
        }
    }

    fun testCompletion() {
        myFixture.configureByText(
            "test.grammar",
            "@top Program { <caret> }\nexpr { \"x\" }\nlist<item> { item }\n@tokens { Number { @digit+ } }",
        )
        myFixture.completeBasic()
        assertContainsElements(myFixture.lookupElementStrings!!, "Program", "expr", "list", "Number")
    }

    fun testCompletionInTokens() {
        myFixture.configureByText("test.grammar", "expr { \"x\" }\n@tokens { Number { <caret> } digits { @digit } }")
        myFixture.completeBasic()
        val lookups = myFixture.lookupElementStrings!!
        assertContainsElements(lookups, "digits", "Number")
        assertDoesntContain(lookups, "expr")
    }

    fun testCompletionOfParameters() {
        myFixture.configureByText("test.grammar", "list<item, separator> { item (<caret>) }")
        myFixture.completeBasic()
        assertContainsElements(myFixture.lookupElementStrings!!, "item", "separator", "list")
    }

    fun testCompletionOfProps() {
        myFixture.configureByText("test.grammar", "@external prop myProp from \"./p\"\na[<caret>] { \"x\" }")
        myFixture.completeBasic()
        assertContainsElements(myFixture.lookupElementStrings!!, "myProp", "closedBy", "group")
    }
}
