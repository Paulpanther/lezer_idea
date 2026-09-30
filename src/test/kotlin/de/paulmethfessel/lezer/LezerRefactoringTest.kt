package de.paulmethfessel.lezer

import com.intellij.refactoring.safeDelete.SafeDeleteHandler
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class LezerRefactoringTest : BasePlatformTestCase() {
    fun testFindUsages() {
        myFixture.configureByText(
            "test.grammar",
            "@top Program { ex<caret>pr* }\nexpr { \"(\" expr \")\" | Number }\n@tokens { Number { @digit+ } }",
        )
        val target = myFixture.elementAtCaret
        assertEquals(2, myFixture.findUsages(target).size)
    }

    fun testFindUsagesOfToken() {
        myFixture.configureByText(
            "test.grammar",
            "@top Program { Number }\n@tokens { Num<caret>ber { @digit+ } @precedence { Number, \"x\" } }",
        )
        assertEquals(2, myFixture.findUsages(myFixture.elementAtCaret).size)
    }

    fun testRenameRule() = doRename(
        "@top Program { ex<caret>pr* }\nexpr { \"(\" expr \")\" }",
        "value",
        "@top Program { value* }\nvalue { \"(\" value \")\" }",
    )

    fun testRenameFromDeclaration() = doRename(
        "@top Program { Number }\n@tokens { Num<caret>ber { @digit+ } @precedence { Number, \"x\" } }",
        "Int",
        "@top Program { Int }\n@tokens { Int { @digit+ } @precedence { Int, \"x\" } }",
    )

    fun testRenameParameterKeepsShadowedRule() = doRename(
        "item { \"x\" }\nlist<it<caret>em> { item (\",\" item)* }\nother { item }",
        "element",
        "item { \"x\" }\nlist<element> { element (\",\" element)* }\nother { item }",
    )

    fun testRenamePrecedence() = doRename(
        "@precedence { ti<caret>mes @left }\na { !times \"x\" }",
        "mul",
        "@precedence { mul @left }\na { !mul \"x\" }",
    )

    fun testRenameDialect() = doRename(
        "@dialects { t<caret>s }\na[@dialect=ts] { \"x\" }",
        "typescript",
        "@dialects { typescript }\na[@dialect=typescript] { \"x\" }",
    )

    fun testRenameExternalPropAlias() = doRename(
        "@external prop myProp as al<caret>ias from \"./p\"\na[alias=x] { \"x\" }",
        "other",
        "@external prop myProp as other from \"./p\"\na[other=x] { \"x\" }",
    )

    fun testSafeDeleteUnusedRule() = doSafeDelete(
        "@top Program { \"x\" }\nun<caret>used { \"y\" }\nlast { \"z\" }",
        "@top Program { \"x\" }\nlast { \"z\" }",
    )

    fun testSafeDeletePrecedenceInList() = doSafeDelete(
        "@precedence { a @left, un<caret>used @left, b @right }",
        "@precedence { a @left, b @right }",
    )

    fun testSafeDeleteLastPrecedenceInList() = doSafeDelete(
        "@precedence { a @left, un<caret>used @left }",
        "@precedence { a @left }",
    )

    fun testSafeDeleteDialect() = doSafeDelete("@dialects { a, <caret>b }", "@dialects { a }")

    private fun doRename(before: String, newName: String, after: String) {
        myFixture.configureByText("test.grammar", before)
        myFixture.renameElementAtCaret(newName)
        myFixture.checkResult(after)
    }

    private fun doSafeDelete(before: String, after: String) {
        myFixture.configureByText("test.grammar", before)
        SafeDeleteHandler.invoke(project, arrayOf(myFixture.elementAtCaret), false)
        myFixture.checkResult(after)
    }
}
