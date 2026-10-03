package de.paulmethfessel.lezer

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.paulmethfessel.lezer.refactoring.LezerNamesValidator

class LezerEditorTest : BasePlatformTestCase() {
    override fun getTestDataPath(): String = "src/test/testData"

    fun testFolding() {
        myFixture.testFolding("$testDataPath/folding/Folding.grammar")
    }

    fun testLineComment() = doCommentTest(
        IdeActions.ACTION_COMMENT_LINE,
        "a { <caret>b }\nc { d }",
        "//a { b }\nc { d <caret>}",
    )

    fun testLineUncomment() = doCommentTest(
        IdeActions.ACTION_COMMENT_LINE,
        "//a { <caret>b }\nc { d }",
        "a { b }\nc { <caret>d }",
    )

    fun testBlockComment() = doCommentTest(
        IdeActions.ACTION_COMMENT_BLOCK,
        "a { <selection>b c</selection> }",
        "a { /*b c*/ }",
    )

    fun testBlockUncomment() = doCommentTest(
        IdeActions.ACTION_COMMENT_BLOCK,
        "a { /*b <caret>c*/ }",
        "a { b c }",
    )

    fun testEnterInBlockComment() {
        myFixture.configureByText("test.grammar", "/*<caret>\na { b }")
        myFixture.type("\n")
        myFixture.checkResult("/*\n<caret>\n */\na { b }")
    }

    fun testQuotesAreClosed() {
        myFixture.configureByText("test.grammar", "a { <caret> }")
        myFixture.type("\"x")
        myFixture.checkResult("a { \"x<caret>\" }")
        // Typing the closing quote steps over it
        myFixture.type("\"")
        myFixture.checkResult("a { \"x\"<caret> }")
    }

    fun testBracesAreClosed() {
        myFixture.configureByText("test.grammar", "a <caret>")
        myFixture.type("{")
        myFixture.checkResult("a {<caret>}")
        myFixture.configureByText("test.grammar", "a { b<caret> }")
        myFixture.type("(")
        myFixture.checkResult("a { b(<caret>) }")
    }

    fun testNamesValidator() {
        val validator = LezerNamesValidator()
        for (name in listOf("expr", "Number", "kw-if", "_x", "über", "from", "tokens")) {
            assertTrue(name, validator.isIdentifier(name, project))
            assertFalse(name, validator.isKeyword(name, project))
        }
        for (name in listOf("_", "", "a b", "@top", "\"x\"", "a.b")) assertFalse(name, validator.isIdentifier(name, project))
    }

    private fun doCommentTest(action: String, before: String, after: String) {
        myFixture.configureByText("test.grammar", before)
        myFixture.performEditorAction(action)
        myFixture.checkResult(after)
    }
}
