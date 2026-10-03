package de.paulmethfessel.lezer.resolve

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.impl.source.resolve.ResolveCache
import de.paulmethfessel.lezer.psi.LezerArgList
import de.paulmethfessel.lezer.psi.LezerElementFactory
import de.paulmethfessel.lezer.psi.LezerNameExpression
import de.paulmethfessel.lezer.psi.LezerNamedElement

class LezerReference private constructor(element: PsiElement, soft: Boolean) :
    PsiReferenceBase<PsiElement>(element, TextRange(0, element.textLength), soft) {

    override fun resolve(): LezerNamedElement? =
        ResolveCache.getInstance(element.project).resolveWithCaching(this, RESOLVER, false, false)

    override fun getVariants(): Array<Any> {
        val declarations = LezerResolver.candidates(element)
            .distinctBy { it.name }
            .map { declaration ->
                LookupElementBuilder.create(declaration)
                    .withIcon(declaration.kind.icon)
                    .withTypeText(declaration.kind.description)
            }
        val builtins = if (LezerResolver.namespaceOf(element) == LezerResolver.Namespace.PROP) {
            LezerResolver.BUILTIN_PROPS.map {
                LookupElementBuilder.create(it).withIcon(AllIcons.Nodes.Property).withTypeText("built-in prop")
            }
        } else {
            emptyList()
        }
        return (declarations + builtins).toTypedArray<Any>()
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        LezerElementFactory.renameIdentifier(element, newElementName)
        return element
    }

    companion object {
        private val RESOLVER = ResolveCache.AbstractResolver<LezerReference, LezerNamedElement> { reference, _ ->
            LezerResolver.resolveUncached(reference.element)
        }

        fun create(element: PsiElement): LezerReference? {
            val namespace = LezerResolver.namespaceOf(element) ?: return null
            // Props like `closedBy` are defined by @lezer/common without a declaration in the grammar
            val soft = namespace == LezerResolver.Namespace.PROP || isTemplateArgument(element)
            return LezerReference(element, soft)
        }

        /**
         * Bare names passed to a parameterized rule (`op<Compare, "==">`) don't have to be declared
         * when the parameter is only used as a node name in props (`op[@name={name}]<name, body>`).
         */
        private fun isTemplateArgument(element: PsiElement): Boolean {
            val argList = element.parent?.parent as? LezerArgList ?: return false
            return argList.parent is LezerNameExpression
        }
    }
}
