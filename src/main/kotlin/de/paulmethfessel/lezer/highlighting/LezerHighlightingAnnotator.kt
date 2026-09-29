package de.paulmethfessel.lezer.highlighting

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import de.paulmethfessel.lezer.psi.*

/** Highlighting that depends on the position of an element in the PSI tree, not only on its token type. */
class LezerHighlightingAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val key = when {
            element.elementType in LezerTokenSets.CONTEXTUAL_KEYWORDS && isKeywordPosition(element) ->
                LezerColors.CONTEXTUAL_KEYWORD

            element is LezerRuleName -> ruleNameKey(element)

            element is LezerPrecedenceName -> LezerColors.PRECEDENCE_NAME
            element is LezerSimpleName && element.parent is LezerProp && element == element.parent.firstChild -> LezerColors.PROP_NAME
            else -> null
        } ?: return
        highlight(element, key, holder)
    }

    /** Contextual keywords that are used as identifiers are wrapped in a name element. */
    private fun isKeywordPosition(element: PsiElement): Boolean =
        element.parent !is LezerRuleName && element.parent !is LezerSimpleName && element.parent !is LezerPrecedenceName

    private fun ruleNameKey(ruleName: LezerRuleName): TextAttributesKey? {
        val parent = ruleName.parent
        return when {
            parent is LezerRuleDeclaration && parent.paramList != null -> LezerColors.TEMPLATE_DECLARATION
            parent is LezerTopRuleDeclaration && parent.paramList != null -> LezerColors.TEMPLATE_DECLARATION
            parent is LezerNameExpression && parent.argList != null -> LezerColors.TEMPLATE_CALL
            LezerPsiUtil.isToken(ruleName) -> LezerColors.TOKEN
            LezerPsiUtil.isTerm(ruleName) -> LezerColors.TERM
            else -> null
        }
    }


    private fun highlight(element: PsiElement, key: TextAttributesKey, holder: AnnotationHolder) {
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(element)
            .textAttributes(key)
            .create()
    }
}
