package de.paulmethfessel.lezer.psi

import com.intellij.psi.util.PsiTreeUtil
import de.paulmethfessel.lezer.resolve.LezerResolver

/**
 * The names of the syntax tree nodes that declarations create: rules and tokens create a node if their name is
 * capitalized or they have an `@name` prop, see https://lezer.codemirror.net/docs/guide/#writing-a-grammar
 */
object LezerNodeNames {
    /**
     * The value of the `@name` prop, if there is one, with quotes removed (`@name="Name"` names the node `Name`). Null if
     * it is built from a parameter (`@name={name}`), since then it depends on the arguments of each use.
     */
    fun explicitName(element: LezerNamedElement): String? {
        val prop = element.pseudoProp("@name") ?: return null
        if (prop.propInterpolationList.isNotEmpty()) return null
        return LezerStrings.unquote(prop.text.substringAfter('=', "")).takeIf { it.isNotEmpty() }
    }

    /** The name of the node [element] creates, or null if it creates none. */
    fun nodeName(element: LezerNamedElement): String? {
        if (!element.kind.isTerm && !element.kind.isToken) return null
        explicitName(element)?.let { return it }
        val name = element.name ?: return null
        return name.takeIf { it.firstOrNull()?.isUpperCase() == true || element.kind == LezerDeclarationKind.TOP_RULE }
    }

    /** The declarations in [file] that create nodes named [nodeName], including inline rules. */
    fun declarationsOf(file: LezerFile, nodeName: String): List<LezerNamedElement> {
        val declarations = LezerResolver.globalRules(file) +
            PsiTreeUtil.findChildrenOfType(file, LezerInlineRuleExpression::class.java)
        return declarations.filter { nodeName(it) == nodeName }
    }
}
