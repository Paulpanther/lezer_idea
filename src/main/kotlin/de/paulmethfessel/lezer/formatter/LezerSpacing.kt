package de.paulmethfessel.lezer.formatter

import com.intellij.formatting.Spacing
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.tree.TokenSet
import de.paulmethfessel.lezer.parser.LezerParserDefinition
import de.paulmethfessel.lezer.psi.LezerTokenSets
import de.paulmethfessel.lezer.psi.LezerTypes.*

/**
 * Spacing between two sibling blocks. Line breaks are kept (unless disabled in the settings), only the
 * spaces within a line are normalized, following the style of the official Lezer grammars.
 */
class LezerSpacing(private val common: CommonCodeStyleSettings, private val custom: LezerCodeStyleSettings) {
    fun between(parent: ASTNode, left: ASTNode, right: ASTNode): Spacing? {
        if (left.psi is PsiErrorElement || right.psi is PsiErrorElement) return null
        val p = parent.elementType
        val l = left.elementType
        val r = right.elementType

        return when {
            l == LINE_COMMENT -> lineBreak()
            l in LezerTokenSets.COMMENTS || r in LezerTokenSets.COMMENTS ->
                Spacing.createSpacing(1, Int.MAX_VALUE, 0, true, common.KEEP_BLANK_LINES_IN_CODE)
            p == LezerParserDefinition.FILE -> lineBreak()

            // Brackets
            l in LezerBlock.OPENING && r in LezerBlock.CLOSING -> space(false)
            p in LezerTokenSets.BRACED_BLOCKS && (l == LBRACE || r == RBRACE) -> space(custom.SPACE_WITHIN_BRACES, beforeClosing = r == RBRACE)
            p == PAREN_EXPRESSION -> space(common.SPACE_WITHIN_PARENTHESES, beforeClosing = r == RPAREN)
            p in TIGHT_BRACKETS && (l in LezerBlock.OPENING || r in LezerBlock.CLOSING) ->
                space(false, beforeClosing = r in LezerBlock.CLOSING)

            // Commas
            r == COMMA -> space(common.SPACE_BEFORE_COMMA)
            l == COMMA -> space(if (p == PROPS) custom.SPACE_AFTER_COMMA_IN_PROPS else common.SPACE_AFTER_COMMA)

            // Tightly bound parts: `name[props]<params>`, `@name=value`, `a.b`, `x*`, `!prec`, `~amb`
            r == PROPS || r == PARAM_LIST || r == ARG_LIST -> space(false)
            // Prop values are concatenated, keep a space between them if there is one: `@name="#" {name}`
            p == PROP && l != EQ && r != EQ -> Spacing.createSpacing(0, 1, 0, false, 0)
            p == PROP || p == NAME_EXPRESSION || p == POSTFIX_EXPRESSION -> space(false)
            p == PRECEDENCE_MARKER || p == AMBIGUITY_MARKER -> space(false)

            else -> space(true)
        }
    }

    private fun space(space: Boolean, beforeClosing: Boolean = false): Spacing {
        val n = if (space) 1 else 0
        val blankLines = if (beforeClosing) common.KEEP_BLANK_LINES_BEFORE_RBRACE else common.KEEP_BLANK_LINES_IN_CODE
        return Spacing.createSpacing(n, n, 0, common.KEEP_LINE_BREAKS, blankLines)
    }

    private fun lineBreak(): Spacing =
        Spacing.createSpacing(0, 0, 1, common.KEEP_LINE_BREAKS, common.KEEP_BLANK_LINES_IN_CODE)

    private companion object {
        /** Brackets that never have spaces inside: `[@name=A]`, `<a, b>`, `{name}`. */
        val TIGHT_BRACKETS = TokenSet.create(PROPS, PARAM_LIST, ARG_LIST, PROP_INTERPOLATION)
    }
}
