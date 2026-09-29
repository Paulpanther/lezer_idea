package de.paulmethfessel.lezer.highlighting

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import de.paulmethfessel.lezer.lexer.LezerLexerAdapter
import de.paulmethfessel.lezer.psi.LezerTokenSets
import de.paulmethfessel.lezer.psi.LezerTypes.*

class LezerSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer(): Lexer = LezerLexerAdapter()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> =
        pack(ATTRIBUTES[tokenType])

    companion object {
        private val ATTRIBUTES = buildMap {
            put(LINE_COMMENT, LezerColors.LINE_COMMENT)
            put(BLOCK_COMMENT, LezerColors.BLOCK_COMMENT)
            put(STRING, LezerColors.STRING)
            put(CHAR_SET, LezerColors.CHAR_SET)
            put(INVERTED_CHAR_SET, LezerColors.CHAR_SET)
            put(CHAR_CLASS, LezerColors.CHAR_CLASS)
            put(ANY_CHAR, LezerColors.ANY_CHAR)

            fillMap(this, LezerTokenSets.DECLARATION_KEYWORDS, LezerColors.KEYWORD)
            fillMap(this, LezerTokenSets.ASSOCIATIVITY_KEYWORDS, LezerColors.ASSOCIATIVITY)
            fillMap(this, LezerTokenSets.SPECIALIZE_KEYWORDS, LezerColors.SPECIALIZE)
            put(AT_NAME, LezerColors.PSEUDO_PROP_NAME)

            put(PIPE, LezerColors.OPERATOR)
            put(STAR, LezerColors.OPERATOR)
            put(PLUS, LezerColors.OPERATOR)
            put(QUESTION, LezerColors.OPERATOR)
            put(BANG, LezerColors.MARKER)
            put(TILDE, LezerColors.MARKER)
            put(EQ, LezerColors.EQ)
            put(LBRACE, LezerColors.BRACES)
            put(RBRACE, LezerColors.BRACES)
            put(LPAREN, LezerColors.PARENTHESES)
            put(RPAREN, LezerColors.PARENTHESES)
            put(LBRACKET, LezerColors.BRACKETS)
            put(RBRACKET, LezerColors.BRACKETS)
            put(LANGLE, LezerColors.ANGLE_BRACKETS)
            put(RANGLE, LezerColors.ANGLE_BRACKETS)
            put(COMMA, LezerColors.COMMA)
            put(DOT, LezerColors.DOT)

            put(TokenType.BAD_CHARACTER, LezerColors.BAD_CHARACTER)
        }
    }
}
