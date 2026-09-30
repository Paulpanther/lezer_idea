package de.paulmethfessel.lezer.refactoring

import com.intellij.lang.refactoring.RefactoringSupportProvider
import com.intellij.psi.PsiElement
import de.paulmethfessel.lezer.psi.LezerDeclarationKind
import de.paulmethfessel.lezer.psi.LezerNamedElement

class LezerRefactoringSupportProvider : RefactoringSupportProvider() {
    override fun isMemberInplaceRenameAvailable(element: PsiElement, context: PsiElement?): Boolean =
        element is LezerNamedElement

    override fun isSafeDeleteAvailable(element: PsiElement): Boolean =
        element is LezerNamedElement && element.kind != LezerDeclarationKind.PARAMETER && !element.kind.isInline
}
