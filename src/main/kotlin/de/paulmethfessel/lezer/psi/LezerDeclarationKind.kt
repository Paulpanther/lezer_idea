package de.paulmethfessel.lezer.psi

import com.intellij.icons.AllIcons
import com.intellij.psi.util.PsiTreeUtil
import javax.swing.Icon

enum class LezerDeclarationKind(
    val description: String,
    val icon: Icon,
    val isTerm: Boolean = false,
    val isToken: Boolean = false,
    val isTemplate: Boolean = false,
) {
    TOP_RULE("top rule", AllIcons.Nodes.Class, isTerm = true),
    TERM("rule", AllIcons.Nodes.Field, isTerm = true),
    TERM_TEMPLATE("parameterized rule", AllIcons.Nodes.Method, isTerm = true, isTemplate = true),
    INLINE_TERM("inline rule", AllIcons.Nodes.Field, isTerm = true),
    TOKEN("token", AllIcons.Nodes.Constant, isToken = true),
    TOKEN_TEMPLATE("parameterized token", AllIcons.Nodes.Method, isToken = true, isTemplate = true),
    INLINE_TOKEN("inline token", AllIcons.Nodes.Constant, isToken = true),
    EXTERNAL_TOKEN("external token", AllIcons.Nodes.Constant, isToken = true),
    PARAMETER("parameter", AllIcons.Nodes.Parameter),
    PRECEDENCE("precedence", AllIcons.Nodes.Tag),
    DIALECT("dialect", AllIcons.Nodes.Enum),
    EXTERNAL_PROP("external prop", AllIcons.Nodes.Property),
    EXTERNAL_TOKENIZER("external tokenizer", AllIcons.Nodes.Function),
    EXTERNAL_SPECIALIZER("external specializer", AllIcons.Nodes.Function),
    PROP_SOURCE("prop source", AllIcons.Nodes.Function),
    CONTEXT("context tracker", AllIcons.Nodes.Function);

    val isInline: Boolean get() = this == INLINE_TERM || this == INLINE_TOKEN

    /** Whether declarations of this kind can be referenced by name from anywhere in the grammar. */
    val isGlobalRule: Boolean get() = (isTerm || isToken) && !isInline

    companion object {
        fun of(element: LezerNamedElement): LezerDeclarationKind = when (element) {
            is LezerTopRuleDeclaration -> TOP_RULE
            is LezerRuleDeclaration -> when {
                isInTokenBlock(element) -> if (element.paramList != null) TOKEN_TEMPLATE else TOKEN
                else -> if (element.paramList != null) TERM_TEMPLATE else TERM
            }
            is LezerInlineRuleExpression -> if (isInTokenBlock(element)) INLINE_TOKEN else INLINE_TERM
            is LezerElseToken -> TOKEN
            is LezerExternalToken -> EXTERNAL_TOKEN
            is LezerParameter -> PARAMETER
            is LezerPrecedence -> PRECEDENCE
            is LezerDialect -> DIALECT
            is LezerExternalPropDeclaration -> EXTERNAL_PROP
            is LezerExternalTokensDeclaration -> EXTERNAL_TOKENIZER
            is LezerExternalSpecializeDeclaration -> EXTERNAL_SPECIALIZER
            is LezerExternalPropSourceDeclaration -> PROP_SOURCE
            is LezerContextDeclaration -> CONTEXT
            else -> error("Unknown named element: $element")
        }

        private fun isInTokenBlock(element: LezerNamedElement): Boolean =
            PsiTreeUtil.getParentOfType(element, LezerTokensBody::class.java, LezerLocalTokensBody::class.java) != null
    }
}
