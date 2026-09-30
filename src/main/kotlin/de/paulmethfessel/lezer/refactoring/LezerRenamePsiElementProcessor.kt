package de.paulmethfessel.lezer.refactoring

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import de.paulmethfessel.lezer.psi.LezerFile
import de.paulmethfessel.lezer.psi.LezerNamedElement

/**
 * Renames only update the grammar: uses in code (styleTags keys, imports from the generated terms file) refer to the
 * generated parser and change when it is regenerated.
 */
class LezerRenamePsiElementProcessor : RenamePsiElementProcessor() {
    override fun canProcessElement(element: PsiElement): Boolean = element is LezerNamedElement

    override fun findReferences(
        element: PsiElement,
        searchScope: SearchScope,
        searchInCommentsAndStrings: Boolean,
    ): Collection<PsiReference> =
        super.findReferences(element, searchScope, searchInCommentsAndStrings).filter { it.element.containingFile is LezerFile }
}
