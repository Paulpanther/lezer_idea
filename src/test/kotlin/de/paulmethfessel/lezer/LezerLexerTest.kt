package de.paulmethfessel.lezer

import com.intellij.lexer.Lexer
import com.intellij.testFramework.LexerTestCase
import de.paulmethfessel.lezer.lexer.LezerLexerAdapter

class LezerLexerTest : LexerTestCase() {
    override fun createLexer(): Lexer = LezerLexerAdapter()

    override fun getDirPath(): String = "src/test/testData/lexer"

    fun testComments() = doTest(
        "// line\n/* block\n */ /* unterminated",
        """
        line comment ('// line')
        WHITE_SPACE ('\n')
        block comment ('/* block\n */')
        WHITE_SPACE (' ')
        block comment ('/* unterminated')
        """.trimIndent(),
    )

    fun testStrings() = doTest(
        """"a\"b" 'c\'d' "open""",
        """
        string ('"a\"b"')
        WHITE_SPACE (' ')
        string (''c\'d'')
        WHITE_SPACE (' ')
        string ('"open')
        """.trimIndent(),
    )

    fun testUnterminatedStringEndsAtLineEnd() = doTest(
        "\"open\nName",
        """
        string ('"open')
        WHITE_SPACE ('\n')
        identifier ('Name')
        """.trimIndent(),
    )

    fun testCharSets() = doTest(
        """$[a-z\]] ![\n"] !prec""",
        """
        character set ('$[a-z\]]')
        WHITE_SPACE (' ')
        inverted character set ('![\n"]')
        WHITE_SPACE (' ')
        ! ('!')
        identifier ('prec')
        """.trimIndent(),
    )

    fun testAtKeywords() = doTest(
        "@top @tokens @topLevel @digit @eof @name @specialize",
        """
        @top ('@top')
        WHITE_SPACE (' ')
        @tokens ('@tokens')
        WHITE_SPACE (' ')
        pseudo prop name ('@topLevel')
        WHITE_SPACE (' ')
        character class ('@digit')
        WHITE_SPACE (' ')
        character class ('@eof')
        WHITE_SPACE (' ')
        pseudo prop name ('@name')
        WHITE_SPACE (' ')
        @specialize ('@specialize')
        """.trimIndent(),
    )

    fun testNamesAndContextualKeywords() = doTest(
        "from fromage _ _x kebab-name Ünïcode",
        """
        from ('from')
        WHITE_SPACE (' ')
        identifier ('fromage')
        WHITE_SPACE (' ')
        _ ('_')
        WHITE_SPACE (' ')
        identifier ('_x')
        WHITE_SPACE (' ')
        identifier ('kebab-name')
        WHITE_SPACE (' ')
        identifier ('Ünïcode')
        """.trimIndent(),
    )

    fun testRuleWithProps() = doTest(
        "A[@name=x]<p>{p+ | ~amb b?}",
        """
        identifier ('A')
        [ ('[')
        pseudo prop name ('@name')
        = ('=')
        identifier ('x')
        ] (']')
        < ('<')
        identifier ('p')
        > ('>')
        { ('{')
        identifier ('p')
        + ('+')
        WHITE_SPACE (' ')
        | ('|')
        WHITE_SPACE (' ')
        ~ ('~')
        identifier ('amb')
        WHITE_SPACE (' ')
        identifier ('b')
        ? ('?')
        } ('}')
        """.trimIndent(),
    )

    fun testBadCharacters() = doTest(
        "a # @",
        """
        identifier ('a')
        WHITE_SPACE (' ')
        BAD_CHARACTER ('#')
        WHITE_SPACE (' ')
        BAD_CHARACTER ('@')
        """.trimIndent(),
    )
}
