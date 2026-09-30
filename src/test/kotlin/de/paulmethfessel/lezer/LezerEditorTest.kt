package de.paulmethfessel.lezer

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.fixtures.BasePlatformTestCase

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

    private fun doCommentTest(action: String, before: String, after: String) {
        myFixture.configureByText("test.grammar", before)
        myFixture.performEditorAction(action)
        myFixture.checkResult(after)
    }
}
