package de.paulmethfessel.lezer.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiReference
import de.paulmethfessel.lezer.resolve.LezerReference

/** Name element that may reference a declaration, depending on where it is used. */
abstract class LezerReferenceElementImpl(node: ASTNode) : ASTWrapperPsiElement(node) {
    override fun getReference(): PsiReference? = LezerReference.create(this)

    override fun getReferences(): Array<PsiReference> = reference?.let { arrayOf(it) } ?: PsiReference.EMPTY_ARRAY
}
