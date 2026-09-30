package de.paulmethfessel.lezer.generator

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ConflictLocatorTest : BasePlatformTestCase() {
    /** Real message for a missing precedence marker. */
    fun testMissingPrecedence() = doTest(
        """
        mExpr { MBinary | "x" }
        MBinary {
          mExpr !times ("*" | "/") mExpr |
          mExpr !and "and" mExpr |
          mExpr <target>"or"</target> mExpr
        }
        """,
        """
        shift/reduce conflict between
          MBinary -> mExpr · "or" mExpr
        and
          MBinary -> mExpr "or" mExpr
        With input:
          Word space "(" mExpr "or" mExpr · "or" …
        The reduction of MBinary is allowed before "or" because of this rule:
          MBinary -> mExpr · "or" mExpr
        Shared origin: mExpr -> · MBinary
        """,
        "Shift/reduce conflict between 'MBinary -> mExpr · \"or\" mExpr' and 'MBinary -> mExpr \"or\" mExpr'",
    )

    fun testSpecializedTemplateArgument() = doTest(
        """
        e { e <target>kw<"or"></target> e | "x" }
        kw<t> { @specialize<W, t> }
        """,
        "shift/reduce conflict between\n  e -> e · W/\"or\" e\nand\n  e -> e W/\"or\" e",
    )

    fun testExpandedOptionalInInlineRule() = doTest(
        """
        e { Bin { e (<target>'+'</target> e)? } | "x" }
        """,
        "shift/reduce conflict between\n  Bin -> e · \"+\" e\nand\n  Bin -> e",
    )

    fun testReduceReduceHighlightsBothAlternatives() {
        myFixture.configureByText("test.grammar", "e { \"a\" | B { \"a\" } }")
        val conflict = ConflictLocator.locate(myFixture.file, "reduce/reduce conflict between\n  B -> \"a\"\nand\n  e -> \"a\"")!!
        assertEquals(listOf("\"a\"", "\"a\""), conflict.ranges.map { it.substring(myFixture.file.text) })
        assertEquals(listOf(14, 4), conflict.ranges.map { it.startOffset })
    }

    fun testUnknownRule() {
        myFixture.configureByText("test.grammar", "e { \"x\" }")
        assertNull(ConflictLocator.locate(myFixture.file, "shift/reduce conflict between\n  x+ -> · \"a\"\nand\n  x+ -> \"a\""))
        assertNull(ConflictLocator.locate(myFixture.file, "Unused rule 'e'"))
    }

    private fun doTest(grammar: String, message: String, description: String? = null) {
        val text = grammar.trimIndent()
        val start = text.indexOf("<target>")
        val source = text.replace("<target>", "").replace("</target>", "")
        val end = text.indexOf("</target>") - "<target>".length
        myFixture.configureByText("test.grammar", source)

        val conflict = ConflictLocator.locate(myFixture.file, message.trimIndent())!!
        assertEquals(listOf(source.substring(start, end)), conflict.ranges.map { it.substring(source) })
        assertEquals(start, conflict.ranges.single().startOffset)
        description?.let { assertEquals(it, conflict.description) }
    }
}
