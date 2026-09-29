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
        LezerTokenType.LINE_COMMENT ('// line')
        WHITE_SPACE ('\n')
        LezerTokenType.BLOCK_COMMENT ('/* block\n */')
        WHITE_SPACE (' ')
        LezerTokenType.BLOCK_COMMENT ('/* unterminated')
        """.trimIndent(),
    )

    fun testStrings() = doTest(
        """"a\"b" 'c\'d' "open""",
        """
        LezerTokenType.STRING ('"a\"b"')
        WHITE_SPACE (' ')
        LezerTokenType.STRING (''c\'d'')
        WHITE_SPACE (' ')
        LezerTokenType.STRING ('"open')
        """.trimIndent(),
    )

    fun testUnterminatedStringEndsAtLineEnd() = doTest(
        "\"open\nName",
        """
        LezerTokenType.STRING ('"open')
        WHITE_SPACE ('\n')
        LezerTokenType.NAME ('Name')
        """.trimIndent(),
    )

    fun testCharSets() = doTest(
        """$[a-z\]] ![\n"] !prec""",
        """
        LezerTokenType.CHAR_SET ('$[a-z\]]')
        WHITE_SPACE (' ')
        LezerTokenType.INVERTED_CHAR_SET ('![\n"]')
        WHITE_SPACE (' ')
        LezerTokenType.! ('!')
        LezerTokenType.NAME ('prec')
        """.trimIndent(),
    )

    fun testAtKeywords() = doTest(
        "@top @tokens @topLevel @digit @eof @name @specialize",
        """
        LezerTokenType.@top ('@top')
        WHITE_SPACE (' ')
        LezerTokenType.@tokens ('@tokens')
        WHITE_SPACE (' ')
        LezerTokenType.AT_NAME ('@topLevel')
        WHITE_SPACE (' ')
        LezerTokenType.CHAR_CLASS ('@digit')
        WHITE_SPACE (' ')
        LezerTokenType.CHAR_CLASS ('@eof')
        WHITE_SPACE (' ')
        LezerTokenType.AT_NAME ('@name')
        WHITE_SPACE (' ')
        LezerTokenType.@specialize ('@specialize')
        """.trimIndent(),
    )

    fun testNamesAndContextualKeywords() = doTest(
        "from fromage _ _x kebab-name Ünïcode",
        """
        LezerTokenType.from ('from')
        WHITE_SPACE (' ')
        LezerTokenType.NAME ('fromage')
        WHITE_SPACE (' ')
        LezerTokenType._ ('_')
        WHITE_SPACE (' ')
        LezerTokenType.NAME ('_x')
        WHITE_SPACE (' ')
        LezerTokenType.NAME ('kebab-name')
        WHITE_SPACE (' ')
        LezerTokenType.NAME ('Ünïcode')
        """.trimIndent(),
    )

    fun testRuleWithProps() = doTest(
        "A[@name=x]<p>{p+ | ~amb b?}",
        """
        LezerTokenType.NAME ('A')
        LezerTokenType.[ ('[')
        LezerTokenType.AT_NAME ('@name')
        LezerTokenType.= ('=')
        LezerTokenType.NAME ('x')
        LezerTokenType.] (']')
        LezerTokenType.< ('<')
        LezerTokenType.NAME ('p')
        LezerTokenType.> ('>')
        LezerTokenType.{ ('{')
        LezerTokenType.NAME ('p')
        LezerTokenType.+ ('+')
        WHITE_SPACE (' ')
        LezerTokenType.| ('|')
        WHITE_SPACE (' ')
        LezerTokenType.~ ('~')
        LezerTokenType.NAME ('amb')
        WHITE_SPACE (' ')
        LezerTokenType.NAME ('b')
        LezerTokenType.? ('?')
        LezerTokenType.} ('}')
        """.trimIndent(),
    )

    fun testBadCharacters() = doTest(
        "a # @",
        """
        LezerTokenType.NAME ('a')
        WHITE_SPACE (' ')
        BAD_CHARACTER ('#')
        WHITE_SPACE (' ')
        BAD_CHARACTER ('@')
        """.trimIndent(),
    )
}
