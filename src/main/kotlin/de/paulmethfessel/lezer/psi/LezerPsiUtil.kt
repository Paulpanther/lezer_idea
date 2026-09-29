package de.paulmethfessel.lezer.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType

object LezerPsiUtil {
    /** Whether [element] is located in a context that only contains tokens (`@tokens`, `@external tokens`, …). */
    fun isInTokenContext(element: PsiElement): Boolean =
        PsiTreeUtil.getParentOfType(
            element,
            LezerTokensBody::class.java,
            LezerLocalTokensBody::class.java,
            LezerExternalTokenSet::class.java,
            LezerTokenPrecedenceBody::class.java,
            LezerConflictBody::class.java,
            LezerSpecializeExpression::class.java,
        ) != null

    /** Names of all terms (nonterminal rules, i.e. rules declared outside of token blocks) in [file]. */
    fun termNames(file: LezerFile): Set<String> = CachedValuesManager.getCachedValue(file) {
        CachedValueProvider.Result.create(collectTermNames(file), file)
    }

    /** Names of all tokens (rules declared in `@tokens`, `@local tokens` or `@external` blocks) in [file]. */
    fun tokenNames(file: LezerFile): Set<String> = CachedValuesManager.getCachedValue(file) {
        CachedValueProvider.Result.create(collectTokenNames(file), file)
    }

    /** Whether [ruleName] declares or references a token. */
    fun isToken(ruleName: LezerRuleName): Boolean {
        val parent = ruleName.parent
        if (isTokenDeclaration(ruleName)) return true
        if (parent !is LezerNameExpression || isParameter(ruleName, ruleName.text)) return false
        val file = ruleName.containingFile as? LezerFile ?: return false
        return ruleName.text in tokenNames(file)
    }

    private fun isTokenDeclaration(ruleName: LezerRuleName): Boolean = when (ruleName.parent) {
        is LezerRuleDeclaration, is LezerInlineRuleExpression -> PsiTreeUtil.getParentOfType(
            ruleName, LezerTokensBody::class.java, LezerLocalTokensBody::class.java,
        ) != null
        is LezerExternalToken, is LezerElseToken -> true
        else -> false
    }

    private fun collectTokenNames(file: LezerFile): Set<String> =
        PsiTreeUtil.findChildrenOfType(file, LezerRuleName::class.java)
            .filter(::isTokenDeclaration)
            .mapTo(mutableSetOf()) { it.text }

    /** Whether [ruleName] declares or references a term. */
    fun isTerm(ruleName: LezerRuleName): Boolean {
        if (isInTokenContext(ruleName)) return false
        val name = ruleName.text
        val parent = ruleName.parent
        if (parent is LezerRuleDeclaration || parent is LezerTopRuleDeclaration || parent is LezerInlineRuleExpression) {
            return true
        }
        if (parent !is LezerNameExpression || isParameter(ruleName, name)) return false
        val file = ruleName.containingFile as? LezerFile ?: return false
        return name in termNames(file)
    }

    private fun isParameter(element: PsiElement, name: String): Boolean {
        val rule = element.parentOfType<LezerRuleDeclaration>() ?: element.parentOfType<LezerTopRuleDeclaration>()
        val params = when (rule) {
            is LezerRuleDeclaration -> rule.paramList
            is LezerTopRuleDeclaration -> rule.paramList
            else -> null
        } ?: return false
        return params.simpleNameList.any { it.text == name }
    }

    private fun collectTermNames(file: LezerFile): Set<String> {
        val rules = file.children.flatMap {
            when (it) {
                is LezerRuleDeclaration, is LezerTopRuleDeclaration -> listOf(it)
                is LezerSkipDeclaration -> it.skipBody?.let { body -> body.ruleDeclarationList + body.topRuleDeclarationList }.orEmpty()
                else -> emptyList()
            }
        }
        val names = mutableSetOf<String>()
        for (rule in rules) {
            when (rule) {
                is LezerRuleDeclaration -> rule.ruleName.text
                is LezerTopRuleDeclaration -> rule.ruleName?.text
                else -> null
            }?.let(names::add)
            PsiTreeUtil.findChildrenOfType(rule, LezerInlineRuleExpression::class.java)
                .mapNotNullTo(names) { it.ruleName?.text }
        }
        return names
    }
}
