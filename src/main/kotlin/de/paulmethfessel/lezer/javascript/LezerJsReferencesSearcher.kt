package de.paulmethfessel.lezer.javascript

import com.intellij.lang.javascript.JavascriptLanguage
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.search.RequestResultProcessor
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.IncorrectOperationException
import com.intellij.util.Processor
import de.paulmethfessel.lezer.psi.LezerNamedElement

/**
 * Finds the uses of rules and tokens in JavaScript/TypeScript, see [LezerJsUsages]. The references can't be renamed,
 * since the terms file is generated and styleTags keys must match the generated node names;
 * [de.paulmethfessel.lezer.refactoring.LezerRenamePsiElementProcessor] leaves them out of renames.
 */
class LezerJsReferencesSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {
    override fun processQuery(parameters: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>) {
        val target = parameters.elementToSearch as? LezerNamedElement ?: return
        if (!target.kind.isGlobalRule) return
        val name = target.name ?: return
        val grammar = target.containingFile?.virtualFile ?: return

        parameters.optimizer.searchWord(
            name,
            parameters.effectiveSearchScope,
            (UsageSearchContext.IN_CODE.toInt() or UsageSearchContext.IN_STRINGS.toInt()).toShort(),
            true,
            target,
            object : RequestResultProcessor(target) {
                override fun processTextOccurrence(element: PsiElement, offsetInElement: Int, consumer: Processor<in PsiReference>): Boolean {
                    if (!element.language.isKindOf(JavascriptLanguage)) return true
                    val code = element.containingFile?.virtualFile ?: return true
                    if (!LezerGrammars.belongTogether(element.project, grammar, code)) return true
                    val offset = element.textRange.startOffset + offsetInElement
                    val usage = LezerJsUsages.usageAt(element, offset)?.takeIf { it.name == name } ?: return true
                    return consumer.process(LezerJsReference(usage.element, usage.range, target))
                }
            },
        )
    }
}

/** A use of a grammar rule in code. */
class LezerJsReference(element: PsiElement, range: TextRange, private val target: LezerNamedElement) :
    PsiReferenceBase<PsiElement>(element, range, true) {
    override fun resolve(): PsiElement = target

    override fun handleElementRename(newElementName: String): PsiElement =
        throw IncorrectOperationException("Names in code using the generated parser can't be renamed")
}
