package de.paulmethfessel.lezer.javascript

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement

/**
 * Navigates from node names in `styleTags` and from names of the generated terms file to the grammar, instead of the
 * generated code.
 */
class LezerJsGotoDeclarationHandler : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor?): Array<PsiElement>? {
        val leaf = sourceElement ?: return null
        val file = leaf.containingFile?.virtualFile ?: return null
        val name = LezerJsUsages.usageAt(leaf, offset)?.name
            ?: (leaf.parent as? JSReferenceExpression)?.takeIf { it.referenceNameElement == leaf }?.let(LezerJsUsages::termsReference)
            ?: return null
        return LezerGrammars.declarations(leaf.project, name, file).toTypedArray<PsiElement>().takeIf { it.isNotEmpty() }
    }
}
