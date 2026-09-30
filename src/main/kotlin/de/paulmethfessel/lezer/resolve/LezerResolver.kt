package de.paulmethfessel.lezer.resolve

import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import de.paulmethfessel.lezer.psi.*

/**
 * Name resolution for Lezer grammars, following the scoping of the lezer-generator:
 * - rule parameters shadow global rules inside the rule body,
 * - terms and tokens share one global namespace, inline rules can't be referenced,
 * - inside token contexts (`@tokens`, `@external` token sets, `@specialize`, …) only tokens are visible.
 */
object LezerResolver {
    /** Prop names that are defined by `@lezer/common` and don't need an `@external prop` declaration. */
    val BUILTIN_PROPS = listOf("closedBy", "openedBy", "group", "isolate", "contextHash", "lookAhead", "mounted")

    enum class Namespace { RULE, PRECEDENCE, PROP, DIALECT }

    /** The namespace the name element [element] references, or null if it is not a reference (e.g. a declaration name). */
    fun namespaceOf(element: PsiElement): Namespace? {
        val parent = element.parent
        return when (element) {
            is LezerRuleName -> when {
                // Scoped names (`a.b`) refer to nested grammars, which aren't supported
                parent is LezerNameExpression && parent.ruleNameList.size == 1 -> Namespace.RULE
                parent is LezerPropInterpolation -> Namespace.RULE
                else -> null
            }
            is LezerPrecedenceName -> if (parent is LezerPrecedenceMarker) Namespace.PRECEDENCE else null
            is LezerPropName -> Namespace.PROP
            is LezerSimpleName -> if (parent is LezerProp && isPseudoProp(parent, "@dialect")) Namespace.DIALECT else null
            else -> null
        }
    }

    fun resolve(element: PsiElement): LezerNamedElement? = candidates(element).firstOrNull { it.name == element.text }

    /** All declarations that are visible at the position of the reference [element]. */
    fun candidates(element: PsiElement): List<LezerNamedElement> {
        val file = element.containingFile as? LezerFile ?: return emptyList()
        val declarations = declarations(file)
        return when (namespaceOf(element)) {
            Namespace.RULE -> {
                val rules = if (isInTokenContext(element)) declarations.rules.filter { it.kind.isToken } else declarations.rules
                parameters(element) + rules
            }
            Namespace.PRECEDENCE -> declarations.precedences
            Namespace.PROP -> declarations.externalProps
            Namespace.DIALECT -> declarations.dialects
            null -> emptyList()
        }
    }

    /** Whether [element] is located in a context that can only reference tokens. */
    fun isInTokenContext(element: PsiElement): Boolean =
        PsiTreeUtil.getParentOfType(
            element,
            LezerTokensBody::class.java,
            LezerLocalTokensBody::class.java,
            LezerExternalTokenSet::class.java,
            LezerSpecializeExpression::class.java,
        ) != null

    private fun parameters(element: PsiElement): List<LezerParameter> {
        val rule = PsiTreeUtil.getParentOfType(element, LezerRuleDeclaration::class.java, LezerTopRuleDeclaration::class.java)
        val params = (rule as? LezerRuleDeclaration)?.paramList ?: (rule as? LezerTopRuleDeclaration)?.paramList
        return params?.parameterList.orEmpty()
    }

    private fun isPseudoProp(prop: LezerProp, name: String): Boolean = prop.firstChild.text == name

    private class Declarations(
        val rules: List<LezerNamedElement>,
        val precedences: List<LezerNamedElement>,
        val externalProps: List<LezerNamedElement>,
        val dialects: List<LezerNamedElement>,
    )

    private fun declarations(file: LezerFile): Declarations = CachedValuesManager.getCachedValue(file) {
        val all = PsiTreeUtil.findChildrenOfType(file, LezerNamedElement::class.java).filter { it.name != null }
        val declarations = Declarations(
            rules = all.filter { it.kind.isGlobalRule },
            precedences = all.filter { it.kind == LezerDeclarationKind.PRECEDENCE },
            externalProps = all.filter { it.kind == LezerDeclarationKind.EXTERNAL_PROP },
            dialects = all.filter { it.kind == LezerDeclarationKind.DIALECT },
        )
        CachedValueProvider.Result.create(declarations, file)
    }
}
