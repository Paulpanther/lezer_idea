package de.paulmethfessel.lezer.generator

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import de.paulmethfessel.lezer.psi.LezerArgList
import de.paulmethfessel.lezer.psi.LezerBody
import de.paulmethfessel.lezer.psi.LezerChoiceExpression
import de.paulmethfessel.lezer.psi.LezerInlineRuleExpression
import de.paulmethfessel.lezer.psi.LezerLiteralExpression
import de.paulmethfessel.lezer.psi.LezerNameExpression
import de.paulmethfessel.lezer.psi.LezerNamedElement
import de.paulmethfessel.lezer.psi.LezerProps
import de.paulmethfessel.lezer.psi.LezerRule
import de.paulmethfessel.lezer.psi.LezerRuleName
import de.paulmethfessel.lezer.psi.LezerStrings
import de.paulmethfessel.lezer.psi.LezerTypes

/**
 * Finds the source of a conflict reported by the generator. Conflict messages have no position, only the
 * conflicting productions, like
 * ```
 * shift/reduce conflict between
 *   MBinary -> mExpr · "or" mExpr
 * and
 *   MBinary -> mExpr "or" mExpr
 * ```
 * The generator expands groups, optionals and choices into separate productions, so they are matched loosely: in the
 * rule with the production's name, the alternative containing most of the production's symbols in order is chosen,
 * and the symbol after the `·` is highlighted (the whole alternative if there is no `·`).
 */
object ConflictLocator {
    data class Conflict(val description: String, val ranges: List<TextRange>)

    private val HEADER = Regex("""^(\S+/\S+ conflict) between$""")
    private val PRODUCTION = Regex("""^\s+(\S+) -> (.*)$""")
    private val SYMBOL = Regex(""""(?:[^"\\]|\\.)*"|\S+""")
    private const val DOT = "·"

    /** Null if the message is not a conflict or none of its productions could be found. */
    fun locate(file: PsiFile, message: String): Conflict? {
        val lines = message.lines()
        val kind = HEADER.matchEntire(lines.firstOrNull() ?: return null)?.groupValues?.get(1) ?: return null
        val and = lines.indexOf("and")
        if (and < 2 || and + 1 >= lines.size) return null
        val productions = listOf(lines[1], lines[and + 1]).mapNotNull { PRODUCTION.matchEntire(it) }
        if (productions.size != 2) return null

        val ranges = productions.mapNotNull { locate(file, it.groupValues[1], it.groupValues[2]) }
        val description = "${kind.replaceFirstChar { it.uppercase() }} between " +
            productions.joinToString(" and ") { "'${it.groupValues[1]} -> ${it.groupValues[2]}'" }
        // A range inside another one is more precise, e.g. the `·` symbol in the alternative of the other production
        val distinct = ranges.distinct().filter { range -> ranges.none { it != range && range.contains(it) } }
        return Conflict(description, distinct).takeIf { distinct.isNotEmpty() }
    }

    private fun locate(file: PsiFile, rule: String, production: String): TextRange? {
        val tokens = SYMBOL.findAll(production).map { it.value }.toList()
        val dot = tokens.indexOf(DOT)
        val symbols = tokens.filter { it != DOT }.map(::symbolKey)

        val alternatives = declarations(file, rule).flatMap { alternatives(it) }
        val best = alternatives.map { it to match(it, symbols) }.maxByOrNull { (_, matched) -> matched.count { it != null } }
            ?: return null
        val (alternative, matched) = best
        val target = if (dot >= 0) matched.getOrNull(dot) else null
        return (target ?: alternative).textRange
    }

    /** Rules, top rules and inline rules with the name, they can be declared more than once in different scopes. */
    private fun declarations(file: PsiFile, name: String): List<LezerBody> =
        PsiTreeUtil.findChildrenOfType(file, LezerNamedElement::class.java).filter { it.name == name }.mapNotNull {
            when (it) {
                is LezerRule -> it.body
                is LezerInlineRuleExpression -> it.body
                else -> null
            }
        }

    private fun alternatives(body: LezerBody): List<PsiElement> {
        val expression = body.expression ?: return emptyList()
        return (expression as? LezerChoiceExpression)?.expressionList ?: listOf(expression)
    }

    /** For each production symbol, the element in the alternative it was matched with (greedily, in order). */
    private fun match(alternative: PsiElement, symbols: List<String>): List<PsiElement?> {
        val matched = arrayOfNulls<PsiElement>(symbols.size)
        var next = 0
        for ((key, element) in symbols(alternative)) {
            if (next < symbols.size && key == symbols[next]) matched[next++] = element
        }
        return matched.toList()
    }

    /**
     * Literal strings and rule names of the alternative in source order. Props are ignored, and so are the bodies of
     * nested inline rules, which the generator shows by their name.
     */
    private fun symbols(alternative: PsiElement): List<Pair<String, PsiElement>> =
        SyntaxTraverser.psiTraverser(alternative)
            .forceIgnore { it is LezerProps || it is LezerBody }
            .traverse()
            .filterMap { element ->
                when {
                    element is LezerRuleName -> element.text to highlighted(element)
                    element.elementType == LezerTypes.STRING && element.parent is LezerLiteralExpression ->
                        LezerStrings.unquote(element.text) to highlighted(element.parent)
                    else -> null
                }
            }
            .toList()

    /** Symbols in template arguments stand for the whole template call, like `kw<"or">`. */
    private fun highlighted(element: PsiElement): PsiElement {
        val argList = PsiTreeUtil.getParentOfType(element, LezerArgList::class.java) ?: return element
        return argList.parent as? LezerNameExpression ?: element
    }

    /** `"or"` and the specialized `Word/"or"` both stand for the literal `or`. */
    private fun symbolKey(symbol: String): String {
        val literal = symbol.substringAfter('/', symbol).takeIf { it.startsWith('"') } ?: symbol
        return if (literal.startsWith('"')) LezerStrings.unquote(literal) else literal
    }
}
