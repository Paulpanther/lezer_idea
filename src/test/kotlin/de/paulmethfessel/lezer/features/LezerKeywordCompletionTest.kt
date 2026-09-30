package de.paulmethfessel.lezer.features

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class LezerKeywordCompletionTest : BasePlatformTestCase() {
    fun testTopLevelDeclarations() {
        val items = complete("@top P { \"a\" }\n@<caret>")
        assertContainsElements(items, "@tokens", "@skip", "@precedence", "@external tokens", "@dialects", "@detectDelim")
        assertDoesntContain(items, "@digit", "@specialize", "@left")
    }

    fun testWithoutAt() {
        assertContainsElements(complete("@top P { \"a\" }\ntok<caret>"), "@tokens")
    }

    fun testInsertBlock() {
        myFixture.configureByText("test.grammar", "@top P { \"a\" }\n@toke<caret>")
        select("@tokens")
        myFixture.checkResult("@top P { \"a\" }\n@tokens {\n  <caret>\n}")
    }

    fun testCharClassesInTokens() {
        val items = complete("@tokens { number { @<caret> } }")
        assertContainsElements(items, "@digit", "@asciiLetter", "@eof")
        assertDoesntContain(items, "@specialize", "@tokens")
    }

    fun testSpecializeInRules() {
        val items = complete("@top P { @<caret> }")
        assertContainsElements(items, "@specialize", "@extend")
        assertDoesntContain(items, "@digit")
    }

    fun testInsertSpecialize() {
        myFixture.configureByText("test.grammar", "@top P { @spec<caret> }")
        myFixture.completeBasic()
        myFixture.checkResult("@top P { @specialize<<caret>> }")
    }

    fun testTokensBlock() {
        assertContainsElements(complete("@tokens {\n  @<caret>\n}"), "@precedence", "@conflict")
        assertContainsElements(complete("@local tokens {\n  @<caret>\n}"), "@else")
    }

    fun testAssociativity() {
        assertSameElements(complete("@precedence { times @<caret> }"), "@left", "@right", "@cut")
    }

    fun testPseudoProps() {
        val items = complete("@top P { a }\na[@<caret>] { \"a\" }")
        assertContainsElements(items, "@name", "@isGroup", "@dialect", "@export", "@dynamicPrecedence", "@inline")
    }

    fun testInsertPseudoPropWithValue() {
        myFixture.configureByText("test.grammar", "@top P { a }\na[@isGr<caret>] { \"a\" }")
        myFixture.completeBasic()
        myFixture.checkResult("@top P { a }\na[@isGroup=<caret>] { \"a\" }")
    }

    fun testExternalKinds() {
        assertContainsElements(complete("@external <caret>"), "tokens", "prop", "propSource", "extend", "specialize")
        assertContainsElements(complete("@external prop myProp <caret>"), "as", "from")
        assertContainsElements(complete("@external tokens insertSemi <caret>"), "from")
        assertContainsElements(complete("@local <caret>"), "tokens")
    }

    /** Completes and selects [item], also if it is not the only one. */
    private fun select(item: String) {
        val elements = myFixture.completeBasic() ?: return
        myFixture.lookup.currentItem = elements.first { it.lookupString == item }
        myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
    }

    private fun complete(text: String): List<String> {
        myFixture.configureByText("test.grammar", text)
        myFixture.completeBasic()
        return myFixture.lookupElementStrings.orEmpty()
    }
}
