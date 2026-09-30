package de.paulmethfessel.lezer.formatter

import com.intellij.formatting.Alignment
import com.intellij.formatting.Block
import com.intellij.formatting.ChildAttributes
import com.intellij.formatting.Indent
import com.intellij.formatting.Spacing
import com.intellij.lang.ASTNode
import com.intellij.psi.TokenType
import com.intellij.psi.formatter.common.AbstractBlock
import com.intellij.psi.tree.TokenSet
import de.paulmethfessel.lezer.parser.LezerParserDefinition
import de.paulmethfessel.lezer.psi.LezerTypes.*

class LezerBlock(
    node: ASTNode,
    private val indent: Indent,
    private val spacing: LezerSpacing,
    alignment: Alignment? = null,
) : AbstractBlock(node, null, alignment) {
    override fun buildChildren(): List<Block> {
        // Alternatives in parentheses or arguments that span several lines are aligned with the first one
        val alternativeAlignment = if (myNode.elementType == CHOICE_EXPRESSION &&
            myNode.treeParent?.elementType in ALIGNED_CHOICE_PARENTS
        ) Alignment.createAlignment() else null

        return generateSequence(myNode.firstChildNode) { it.treeNext }
            .filter { it.elementType != TokenType.WHITE_SPACE && it.textLength > 0 }
            .mapIndexed { i, child ->
                val alignment = alternativeAlignment.takeIf { child.elementType != PIPE }
                LezerBlock(child, childIndent(child, isFirst = i == 0), spacing, alignment)
            }
            .toList()
    }

    override fun getIndent(): Indent = indent

    override fun getSpacing(child1: Block?, child2: Block): Spacing? {
        if (child1 !is LezerBlock || child2 !is LezerBlock) return null
        return spacing.between(myNode, child1.node, child2.node)
    }

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes {
        val type = myNode.elementType
        val indent = when {
            type in BRACKETED -> Indent.getNormalIndent()
            type == SEQUENCE_EXPRESSION && myNode.treeParent?.elementType == CHOICE_EXPRESSION ->
                Indent.getContinuationIndent()
            else -> Indent.getNoneIndent()
        }
        return ChildAttributes(indent, null)
    }

    override fun isLeaf(): Boolean = myNode.firstChildNode == null

    private fun childIndent(child: ASTNode, isFirst: Boolean): Indent {
        val type = myNode.elementType
        return when {
            type == LezerParserDefinition.FILE -> Indent.getNoneIndent()
            type in BRACKETED -> if (child.elementType in OPENING || child.elementType in CLOSING) {
                Indent.getNoneIndent()
            } else {
                Indent.getNormalIndent()
            }
            // Continued lines of an alternative, like the `ckw<"from">` line in
            //   kw<"import"> (Star ckw<"as"> VariableDefinition | commaSep<VariableDefinition>)
            //     ckw<"from"> String semi |
            type == SEQUENCE_EXPRESSION ->
                if (!isFirst && myNode.treeParent?.elementType == CHOICE_EXPRESSION) Indent.getContinuationIndent()
                else Indent.getNoneIndent()
            type == CHOICE_EXPRESSION -> Indent.getNoneIndent()
            // Keep braced blocks aligned with the start of their declaration, continue everything else
            isFirst || child.elementType in BRACKETED -> Indent.getNoneIndent()
            else -> Indent.getContinuationIndent()
        }
    }

    companion object {
        /** Elements whose content is enclosed in (and indented relative to) a pair of brackets. */
        val BRACKETED = TokenSet.create(
            BODY, PRECEDENCE_BODY, TOKENS_BODY, LOCAL_TOKENS_BODY, TOKEN_PRECEDENCE_BODY, CONFLICT_BODY,
            EXTERNAL_TOKEN_SET, DIALECTS_BODY, SKIP_BODY, PAREN_EXPRESSION, PROPS, PARAM_LIST, ARG_LIST,
            PROP_INTERPOLATION,
        )

        private val ALIGNED_CHOICE_PARENTS = TokenSet.create(PAREN_EXPRESSION, ARG_LIST)

        val OPENING = TokenSet.create(LBRACE, LPAREN, LBRACKET, LANGLE)
        val CLOSING = TokenSet.create(RBRACE, RPAREN, RBRACKET, RANGLE)
    }
}
