package de.paulmethfessel.lezer

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class LezerErrorAnnotatorTest : BasePlatformTestCase() {
    private fun doTest(text: String) {
        myFixture.configureByText("test.grammar", text)
        myFixture.checkHighlighting(false, false, false)
    }

    fun testMissingClosingBraceAtEndOfLine() = doTest(
        """
        @skip { space<EOLError descr="'}' expected, got '@skip'"></EOLError>

        @skip {} {
          @top Program { Statement }
        }
        """.trimIndent(),
    )

    fun testMissingClosingBraceBeforeToken() = doTest(
        "a { b <error descr=\"'}' expected, got '@top'\">@top</error> Program { a }",
    )

    fun testUnexpectedTokenInList() = doTest(
        "list<a <error descr=\"',' or '>' expected, got 'b'\">b</error>> { a }",
    )

    fun testUnexpectedTokenInPrecedence() = doTest(
        "@precedence { a @left <error descr=\"',' or '}' expected, got '\$[a]'\">\$[a]</error> }",
    )

    fun testMissingBody() = doTest(
        "Rule<EOLError descr=\"'<', '[' or '{' expected, got '@top'\"></EOLError>\n@top Program { Rule }",
    )

    fun testUnterminatedStringCausesMissingBrace() = doTest(
        """
        @local tokens {
          stringEscape { <error descr="Unterminated string literal">"\\ _ }</error>
          templateStart[@name="{"] { "{" }<EOLError descr="'}' expected, got '@else'"></EOLError>
          @else content
        }
        """.trimIndent(),
    )

    fun testUnterminatedCharSet() = doTest("chars { <error descr=\"Unterminated character set\">\$[a-z</error>\n}")

    fun testUnterminatedBlockComment() = doTest("a { \"x\" }\n<error descr=\"Unterminated block comment\">/* open</error>")

    fun testBadCharacter() = doTest("bad { \"x\" <error descr=\"Unexpected character '#'\">#</error> }")

    fun testUnexpectedTopLevelToken() = doTest("a { \"x\" } <error descr=\"'@bogus' unexpected\">@bogus</error>")

    fun testValidGrammarHasNoErrors() {
        myFixture.configureByText("test.grammar", java.io.File("src/test/testData/parser/real/javascript.grammar").readText())
        myFixture.checkHighlighting(false, false, false)
    }
}
