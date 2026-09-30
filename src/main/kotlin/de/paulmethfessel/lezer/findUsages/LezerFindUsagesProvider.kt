package de.paulmethfessel.lezer.findUsages

import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import de.paulmethfessel.lezer.lexer.LezerLexerAdapter
import de.paulmethfessel.lezer.psi.LezerNamedElement
import de.paulmethfessel.lezer.psi.LezerTokenSets

class LezerFindUsagesProvider : FindUsagesProvider {
    override fun getWordsScanner(): WordsScanner =
        DefaultWordsScanner(LezerLexerAdapter(), LezerTokenSets.IDENTIFIERS, LezerTokenSets.COMMENTS, LezerTokenSets.STRINGS)

    override fun canFindUsagesFor(element: PsiElement): Boolean = element is LezerNamedElement && element.name != null

    override fun getHelpId(element: PsiElement): String? = null

    override fun getType(element: PsiElement): String = (element as? LezerNamedElement)?.kind?.description.orEmpty()

    override fun getDescriptiveName(element: PsiElement): String = (element as? LezerNamedElement)?.name.orEmpty()

    override fun getNodeText(element: PsiElement, useFullName: Boolean): String = getDescriptiveName(element)
}
