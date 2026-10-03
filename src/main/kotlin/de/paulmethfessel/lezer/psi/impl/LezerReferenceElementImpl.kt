package de.paulmethfessel.lezer.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiReference
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import de.paulmethfessel.lezer.resolve.LezerReference

/** Name element that may reference a declaration, depending on where it is used. */
abstract class LezerReferenceElementImpl(node: ASTNode) : ASTWrapperPsiElement(node) {
    /** The same instance until the PSI changes, so that [com.intellij.psi.impl.source.resolve.ResolveCache] can cache it. */
    override fun getReference(): PsiReference? = CachedValuesManager.getCachedValue(this) {
        CachedValueProvider.Result.create(LezerReference.create(this), PsiModificationTracker.MODIFICATION_COUNT)
    }

    override fun getReferences(): Array<PsiReference> = reference?.let { arrayOf(it) } ?: PsiReference.EMPTY_ARRAY
}
