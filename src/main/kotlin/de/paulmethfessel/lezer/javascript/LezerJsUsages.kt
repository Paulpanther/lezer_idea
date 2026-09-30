package de.paulmethfessel.lezer.javascript

import com.intellij.lang.ecmascript6.psi.ES6ImportExportDeclaration
import com.intellij.lang.ecmascript6.psi.ES6ImportExportSpecifier
import com.intellij.lang.javascript.psi.JSArgumentList
import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.lang.javascript.psi.JSObjectLiteralExpression
import com.intellij.lang.javascript.psi.JSProperty
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

/**
 * Places in JavaScript/TypeScript code that refer to grammar rules by name:
 * - node names in the keys of `styleTags({ "Name Parent/Child/...": t.x })` from `@lezer/highlight`,
 * - names imported from the generated terms file: `import {Name} from "./parser.terms.js"`.
 */
object LezerJsUsages {
    /** A rule name at [range] (relative to [element]). */
    data class Usage(val element: PsiElement, val range: TextRange, val name: String)

    /** The rule name at [offset] (in the file) if [leaf] is part of a styleTags key or a terms import. */
    fun usageAt(leaf: PsiElement, offset: Int): Usage? =
        styleTagNames(leaf).firstOrNull { it.range.shiftRight(it.element.textRange.startOffset).containsOffset(offset) }
            ?: termsImport(leaf)

    /** All node names in [leaf] if it is the key of a property in a `styleTags` call. */
    fun styleTagNames(leaf: PsiElement): List<Usage> {
        val property = PsiTreeUtil.getParentOfType(leaf, JSProperty::class.java, false) ?: return emptyList()
        val key = property.nameIdentifier ?: return emptyList()
        if (!PsiTreeUtil.isAncestor(key, leaf, false) || !isStyleTagsArgument(property)) return emptyList()
        return SelectorNames.parse(key.text).map { (name, range) -> Usage(key, range, name) }
    }

    /** The imported name if [leaf] is the name in an import specifier from a terms file. */
    fun termsImport(leaf: PsiElement): Usage? {
        val specifier = leaf.parent as? ES6ImportExportSpecifier ?: return null
        if (specifier.referenceNameElement != leaf || !isTermsModule(specifier.declaration)) return null
        return Usage(leaf, TextRange(0, leaf.textLength), specifier.referenceName ?: return null)
    }

    /** The rule name of a code reference to a name imported from a terms file, like `Name` or `terms.Name`. */
    fun termsReference(reference: JSReferenceExpression): String? {
        val qualifier = reference.qualifier as? JSReferenceExpression
        if (qualifier == null) {
            val resolved = reference.resolve() ?: return null
            val declaration = PsiTreeUtil.getParentOfType(resolved, ES6ImportExportDeclaration::class.java, false)
            if (declaration == null || !isTermsModule(declaration)) return null
            return PsiTreeUtil.getParentOfType(resolved, ES6ImportExportSpecifier::class.java, false)?.referenceName
        }
        // `import * as terms from "./parser.terms.js"; terms.Name`, which doesn't resolve if the file isn't generated yet
        val namespace = qualifier.resolve() ?: return null
        val namespaceImport = PsiTreeUtil.getParentOfType(namespace, ES6ImportExportDeclaration::class.java, false) ?: return null
        return reference.referenceName.takeIf { isTermsModule(namespaceImport) }
    }

    private fun isStyleTagsArgument(property: JSProperty): Boolean {
        val objectLiteral = property.parent as? JSObjectLiteralExpression ?: return false
        val call = (objectLiteral.parent as? JSArgumentList)?.parent as? JSCallExpression ?: return false
        return (call.methodExpression as? JSReferenceExpression)?.referenceName == "styleTags"
    }

    private fun isTermsModule(declaration: ES6ImportExportDeclaration?): Boolean {
        val module = declaration?.fromClause?.referenceText?.trim('"', '\'', '`') ?: return false
        return module.replace(MODULE_EXTENSION, "").endsWith(".terms")
    }

    private val MODULE_EXTENSION = Regex("""\.(js|mjs|cjs|ts|mts|cts)$""")
}

/**
 * Node names in a styleTags key, see https://lezer.codemirror.net/docs/ref/#highlight.styleTags: space-separated
 * selectors of `/`-separated names, with an optional `/...` or `!` suffix and `*` as a wildcard. Names in quotes are
 * literal tokens like `"("` and don't refer to rules.
 */
object SelectorNames {
    /** Names with their ranges in [key], which is the source text of the key including its JS quotes. */
    fun parse(key: String): List<Pair<String, TextRange>> {
        val quoted = key.length >= 2 && key.first() in "'\"`" && key.last() == key.first()
        val start = if (quoted) 1 else 0
        val end = if (quoted) key.length - 1 else key.length
        val names = mutableListOf<Pair<String, TextRange>>()
        var inLiteral = false
        var i = start
        while (i < end) {
            val c = key[i]
            when {
                // `"` in a double-quoted JS string is escaped as `\"`
                c == '"' -> inLiteral = !inLiteral
                !inLiteral && isNameChar(c) -> {
                    val nameStart = i
                    while (i < end && isNameChar(key[i])) i++
                    names += key.substring(nameStart, i) to TextRange(nameStart, i)
                    continue
                }
            }
            i++
        }
        return names
    }

    private fun isNameChar(c: Char) = c.isLetterOrDigit() || c == '_' || c == '-'
}
