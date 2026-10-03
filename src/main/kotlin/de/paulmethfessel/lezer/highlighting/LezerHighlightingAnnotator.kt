package de.paulmethfessel.lezer.highlighting

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import de.paulmethfessel.lezer.psi.*
import de.paulmethfessel.lezer.resolve.LezerResolver

/**
 * Highlighting that depends on the position of an element in the PSI tree, not only on its token type. It only looks at
 * the file itself, so it also works while indexing.
 */
class LezerHighlightingAnnotator : Annotator, DumbAware {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val key = when {
            element.elementType in LezerTokenSets.CONTEXTUAL_KEYWORDS && isKeywordPosition(element) ->
                LezerColors.CONTEXTUAL_KEYWORD

            element is LezerRuleName -> ruleNameKey(element)
            element is LezerPrecedenceName -> LezerColors.PRECEDENCE_NAME
            element is LezerPropName -> LezerColors.PROP_NAME
            else -> null
        } ?: return
        highlight(element, key, holder)
    }

    /** Contextual keywords that are used as identifiers are wrapped in a name element. */
    private fun isKeywordPosition(element: PsiElement): Boolean = when (element.parent) {
        is LezerRuleName, is LezerSimpleName, is LezerPrecedenceName, is LezerPropName, is LezerParameter, is LezerDialect -> false
        else -> true
    }

    private fun ruleNameKey(ruleName: LezerRuleName): TextAttributesKey? {
        val parent = ruleName.parent
        if (parent is LezerNamedElement && parent.nameIdentifier == ruleName) return kindKey(parent.kind, declaration = true)
        if (parent is LezerNameExpression && parent.argList != null) return LezerColors.TEMPLATE_CALL
        val target = LezerResolver.resolve(ruleName) ?: return null
        return kindKey(target.kind, declaration = false)
    }

    private fun kindKey(kind: LezerDeclarationKind, declaration: Boolean): TextAttributesKey? = when {
        kind.isTemplate -> if (declaration) LezerColors.TEMPLATE_DECLARATION else LezerColors.TEMPLATE_CALL
        kind.isToken -> LezerColors.TOKEN
        kind.isTerm -> LezerColors.TERM
        else -> null
    }

    private fun highlight(element: PsiElement, key: TextAttributesKey, holder: AnnotationHolder) {
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(element)
            .textAttributes(key)
            .create()
    }
}
