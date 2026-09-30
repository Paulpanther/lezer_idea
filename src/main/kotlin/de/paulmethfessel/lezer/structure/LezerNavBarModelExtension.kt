package de.paulmethfessel.lezer.structure

import com.intellij.ide.navigationToolbar.StructureAwareNavBarModelExtension
import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import de.paulmethfessel.lezer.LezerLanguage
import javax.swing.Icon

class LezerNavBarModelExtension : StructureAwareNavBarModelExtension() {
    override val language: Language = LezerLanguage

    override fun getPresentableText(item: Any?): String? = (item as? PsiElement)?.let(LezerStructure::presentableText)

    override fun getIcon(item: Any?): Icon? = (item as? PsiElement)?.let(LezerStructure::icon)
}
