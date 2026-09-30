package de.paulmethfessel.lezer.structure

import com.intellij.icons.AllIcons
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import de.paulmethfessel.lezer.psi.*
import javax.swing.Icon

/** Shared structure of a Lezer grammar for the structure view and the navigation bar. */
object LezerStructure {
    /** Declaration blocks without a name of their own that group named declarations. */
    val BLOCK_CLASSES = arrayOf(
        LezerTokensDeclaration::class.java,
        LezerLocalTokensDeclaration::class.java,
        LezerSkipDeclaration::class.java,
        LezerPrecedenceDeclaration::class.java,
        LezerDialectsDeclaration::class.java,
    )

    fun isBlock(element: PsiElement): Boolean = BLOCK_CLASSES.any { it.isInstance(element) }

    /** Whether [element] is shown as a node in the structure view. */
    fun isNode(element: PsiElement): Boolean = when (element) {
        is LezerParameter -> false
        is LezerNamedElement -> element.name != null
        is LezerSkipDeclaration -> element.skipBody != null
        else -> isBlock(element)
    }

    fun children(element: PsiElement): List<NavigatablePsiElement> {
        val children = when (element) {
            is LezerFile -> element.children.toList()
            is LezerTokensDeclaration -> element.tokensBody?.children?.toList()
            is LezerLocalTokensDeclaration -> element.localTokensBody?.children?.toList()
            is LezerSkipDeclaration -> element.skipBody?.children?.toList()
            is LezerPrecedenceDeclaration -> element.precedenceBody?.precedenceList
            is LezerDialectsDeclaration -> element.dialectsBody?.dialectList
            is LezerExternalTokensDeclaration -> element.externalTokenSet?.externalTokenList
            is LezerExternalSpecializeDeclaration -> element.externalTokenSet?.externalTokenList
            is LezerRuleDeclaration, is LezerTopRuleDeclaration, is LezerInlineRuleExpression -> inlineRules(element)
            else -> null
        }
        return children.orEmpty().filter(::isNode).filterIsInstance<NavigatablePsiElement>()
    }

    /** Named inline rules in the body of [rule], without the ones nested in other named inline rules. */
    private fun inlineRules(rule: PsiElement): List<PsiElement> {
        val result = mutableListOf<PsiElement>()
        fun visit(element: PsiElement) {
            for (child in element.children) {
                if (child is LezerInlineRuleExpression && child.name != null) result += child else visit(child)
            }
        }
        rule.children.filter { it is LezerBody }.forEach(::visit)
        return result
    }

    fun presentableText(element: PsiElement): String? = when (element) {
        is LezerFile -> element.name
        is LezerNamedElement -> element.presentation?.presentableText
        is LezerTokensDeclaration -> "@tokens"
        is LezerLocalTokensDeclaration -> "@local tokens"
        is LezerSkipDeclaration -> "@skip"
        is LezerPrecedenceDeclaration -> "@precedence"
        is LezerDialectsDeclaration -> "@dialects"
        else -> null
    }

    fun icon(element: PsiElement): Icon? = when (element) {
        is LezerFile -> element.getIcon(0)
        is LezerNamedElement -> element.kind.icon
        else -> if (isBlock(element)) AllIcons.Nodes.Folder else null
    }
}
