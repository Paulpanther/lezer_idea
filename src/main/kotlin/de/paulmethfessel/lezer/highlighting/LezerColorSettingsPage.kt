package de.paulmethfessel.lezer.highlighting

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import de.paulmethfessel.lezer.LezerIcons
import javax.swing.Icon

class LezerColorSettingsPage : ColorSettingsPage {
    override fun getIcon(): Icon = LezerIcons.FILE

    override fun getHighlighter(): SyntaxHighlighter = LezerSyntaxHighlighter()

    override fun getDemoText(): String = DEMO_TEXT

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> = TAGS

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getDisplayName(): String = "Lezer Grammar"

    companion object {
        private val DESCRIPTORS = arrayOf(
            AttributesDescriptor("Comments//Line comment", LezerColors.LINE_COMMENT),
            AttributesDescriptor("Comments//Block comment", LezerColors.BLOCK_COMMENT),
            AttributesDescriptor("Literals//String", LezerColors.STRING),
            AttributesDescriptor("Literals//Character set", LezerColors.CHAR_SET),
            AttributesDescriptor("Literals//Character class", LezerColors.CHAR_CLASS),
            AttributesDescriptor("Literals//Any character", LezerColors.ANY_CHAR),
            AttributesDescriptor("Keywords//Declaration keyword", LezerColors.KEYWORD),
            AttributesDescriptor("Keywords//Associativity", LezerColors.ASSOCIATIVITY),
            AttributesDescriptor("Keywords//Specialize and extend", LezerColors.SPECIALIZE),
            AttributesDescriptor("Keywords//Contextual keyword", LezerColors.CONTEXTUAL_KEYWORD),
            AttributesDescriptor("Identifiers//Term", LezerColors.TERM),
            AttributesDescriptor("Identifiers//Token", LezerColors.TOKEN),
            AttributesDescriptor("Identifiers//Parameterized rule declaration", LezerColors.TEMPLATE_DECLARATION),
            AttributesDescriptor("Identifiers//Parameterized rule call", LezerColors.TEMPLATE_CALL),
            AttributesDescriptor("Identifiers//Precedence name", LezerColors.PRECEDENCE_NAME),
            AttributesDescriptor("Props//Prop name", LezerColors.PROP_NAME),
            AttributesDescriptor("Props//Pseudo prop name", LezerColors.PSEUDO_PROP_NAME),
            AttributesDescriptor("Operators//Operator", LezerColors.OPERATOR),
            AttributesDescriptor("Operators//Precedence and ambiguity marker", LezerColors.MARKER),
            AttributesDescriptor("Operators//Equals", LezerColors.EQ),
            AttributesDescriptor("Braces and Operators//Braces", LezerColors.BRACES),
            AttributesDescriptor("Braces and Operators//Parentheses", LezerColors.PARENTHESES),
            AttributesDescriptor("Braces and Operators//Brackets", LezerColors.BRACKETS),
            AttributesDescriptor("Braces and Operators//Angle brackets", LezerColors.ANGLE_BRACKETS),
            AttributesDescriptor("Braces and Operators//Comma", LezerColors.COMMA),
            AttributesDescriptor("Braces and Operators//Dot", LezerColors.DOT),
            AttributesDescriptor("Bad character", LezerColors.BAD_CHARACTER),
        )

        private val TAGS = mapOf(
            "kw" to LezerColors.CONTEXTUAL_KEYWORD,
            "term" to LezerColors.TERM,
            "tok" to LezerColors.TOKEN,
            "tpl" to LezerColors.TEMPLATE_DECLARATION,
            "call" to LezerColors.TEMPLATE_CALL,
            "prec" to LezerColors.PRECEDENCE_NAME,
            "prop" to LezerColors.PROP_NAME,
        )

        private val DEMO_TEXT = """
            // A small expression language
            @precedence { <prec>times</prec> @left, <prec>plus</prec> @left }

            @top <term>Program</term> { <term>expression</term>* }

            <term>expression</term>[@isGroup=Expression] {
              <tok>Number</tok> |
              <term>BinaryExpression</term> {
                <term>expression</term> !<prec>times</prec> "*" <term>expression</term> |
                <term>expression</term> !<prec>plus</prec> ("+" | "-") <term>expression</term>
              } |
              <term>ParenExpression</term>[<prop>closedBy</prop>=")"] { "(" <call>commaSep</call><<term>expression</term>> ")" } |
              <term>Keyword</term> { <call>kw</call><"true"> | <call>kw</call><"false"> }
            }

            <tpl>commaSep</tpl><item> { item ("," item)* }

            <tpl>kw</tpl><term> { @specialize[@name={term}]<<tok>Identifier</tok>, term> }

            @skip { <tok>space</tok> | <tok>LineComment</tok> }

            @tokens {
              <tok>Identifier</tok> { (@asciiLetter | "_")+ }
              <tok>Number</tok> { @digit+ ("." @digit*)? }
              <tok>String</tok> { '"' (![\\"] | "\\" _)* '"' }
              <tok>space</tok> { $[ \t\n\r]+ }
              <tok>LineComment</tok> { "//" ![\n]* }
              @precedence { <tok>Number</tok>, <tok>Identifier</tok> }
              "(" ")"
            }

            /* External tokens and props */
            @external <kw>tokens</kw> insertSemicolon <kw>from</kw> "./tokens" { <tok>InsertSemi</tok> }
            @external <kw>propSource</kw> highlighting <kw>from</kw> "./highlight"

            @detectDelim
        """.trimIndent()
    }
}
