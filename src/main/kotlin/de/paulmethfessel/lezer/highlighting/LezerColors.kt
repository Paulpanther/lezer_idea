package de.paulmethfessel.lezer.highlighting

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as Default
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey

/** Color scheme keys for Lezer grammar files, roughly following `highlight.js` of the official Lezer grammar. */
object LezerColors {
    val LINE_COMMENT = key("LEZER_LINE_COMMENT", Default.LINE_COMMENT)
    val BLOCK_COMMENT = key("LEZER_BLOCK_COMMENT", Default.BLOCK_COMMENT)
    val STRING = key("LEZER_STRING", Default.STRING)
    val CHAR_SET = key("LEZER_CHAR_SET", Default.VALID_STRING_ESCAPE)
    val CHAR_CLASS = key("LEZER_CHAR_CLASS", Default.KEYWORD)
    val ANY_CHAR = key("LEZER_ANY_CHAR", Default.VALID_STRING_ESCAPE)

    val KEYWORD = key("LEZER_KEYWORD", Default.KEYWORD)
    val ASSOCIATIVITY = key("LEZER_ASSOCIATIVITY", Default.KEYWORD)
    val SPECIALIZE = key("LEZER_SPECIALIZE", Default.KEYWORD)
    val CONTEXTUAL_KEYWORD = key("LEZER_CONTEXTUAL_KEYWORD", Default.KEYWORD)

    val TERM = key("LEZER_TERM", Default.INSTANCE_FIELD)
    val TOKEN = key("LEZER_TOKEN", Default.STRING)
    val TEMPLATE_DECLARATION = key("LEZER_TEMPLATE_DECLARATION", Default.FUNCTION_DECLARATION)
    val TEMPLATE_CALL = key("LEZER_TEMPLATE_CALL", Default.FUNCTION_CALL)
    val PRECEDENCE_NAME = key("LEZER_PRECEDENCE_NAME", Default.LABEL)
    val PROP_NAME = key("LEZER_PROP_NAME", Default.METADATA)
    val PSEUDO_PROP_NAME = key("LEZER_PSEUDO_PROP_NAME", Default.METADATA)

    val OPERATOR = key("LEZER_OPERATOR", Default.OPERATION_SIGN)
    val MARKER = key("LEZER_MARKER", Default.OPERATION_SIGN)
    val BRACES = key("LEZER_BRACES", Default.BRACES)
    val PARENTHESES = key("LEZER_PARENTHESES", Default.PARENTHESES)
    val BRACKETS = key("LEZER_BRACKETS", Default.BRACKETS)
    val ANGLE_BRACKETS = key("LEZER_ANGLE_BRACKETS", Default.BRACKETS)
    val COMMA = key("LEZER_COMMA", Default.COMMA)
    val DOT = key("LEZER_DOT", Default.DOT)
    val EQ = key("LEZER_EQ", Default.OPERATION_SIGN)

    val BAD_CHARACTER = key("LEZER_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER)

    private fun key(name: String, fallback: TextAttributesKey) = createTextAttributesKey(name, fallback)
}
