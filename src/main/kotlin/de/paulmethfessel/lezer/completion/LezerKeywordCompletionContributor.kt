package de.paulmethfessel.lezer.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.util.ProcessingContext
import de.paulmethfessel.lezer.LezerLanguage
import de.paulmethfessel.lezer.psi.*

/**
 * Completes keywords depending on the position: declaration keywords at the top level, `@precedence`/`@conflict` in
 * token blocks, character classes in tokens, `@specialize`/`@extend` in rules, associativities in `@precedence`,
 * pseudo-props in props and the contextual keywords of `@external` and `@local` declarations.
 */
class LezerKeywordCompletionContributor : CompletionContributor() {
    init {
        extend(CompletionType.BASIC, psiElement().withLanguage(LezerLanguage), object : CompletionProvider<CompletionParameters>() {
            override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) =
                addKeywords(parameters, result)
        })
    }

    private fun addKeywords(parameters: CompletionParameters, result: CompletionResultSet) {
        val position = parameters.position
        val typed = position.text.substring(0, (parameters.offset - position.textRange.startOffset).coerceIn(0, position.textLength))

        val contextual = contextualKeywords(position)
        if (contextual != null) {
            if (!typed.startsWith("@")) contextual.forEach { result.addElement(keyword(it, " ")) }
            return
        }

        val context = LezerCompletionContext.of(position)
        // Without `@`, keywords are only offered where declarations start, not among the names of expressions
        if (!typed.startsWith("@") && context !in STATEMENT_CONTEXTS) return
        val keywords = when (context) {
            LezerCompletionContext.TOP_LEVEL -> DECLARATIONS
            LezerCompletionContext.TOKENS -> TOKENS_BLOCK
            LezerCompletionContext.LOCAL_TOKENS -> TOKENS_BLOCK + keyword("@else", " ")
            LezerCompletionContext.SKIP_RULES -> listOf(keyword("@top", " "))
            LezerCompletionContext.EXTERNAL_TOKEN_SET -> listOf(keyword("@conflict", " {  }", caret = 3))
            LezerCompletionContext.PRECEDENCE ->
                if (PsiTreeUtil.prevVisibleLeaf(position)?.elementType in LezerTokenSets.IDENTIFIERS) ASSOCIATIVITY else return
            LezerCompletionContext.RULE -> SPECIALIZE
            LezerCompletionContext.TOKEN_RULE -> CHAR_CLASSES
            LezerCompletionContext.PROPS -> PSEUDO_PROPS
            LezerCompletionContext.LIST -> return
        }
        // `@` is not part of an identifier, so the default prefix doesn't include it
        val resultSet = if (typed.startsWith("@")) result.withPrefixMatcher(typed) else result
        keywords.forEach { resultSet.addElement(it) }
    }

    /** `tokens` after `@local`, the kind after `@external` and `from`/`as` after the imported name. */
    private fun contextualKeywords(position: PsiElement): List<String>? {
        val previous = PsiTreeUtil.prevVisibleLeaf(position) ?: return null
        return when (previous.elementType) {
            LezerTypes.AT_LOCAL -> listOf("tokens")
            LezerTypes.AT_EXTERNAL -> listOf("tokens", "prop", "propSource", "extend", "specialize")
            else -> {
                val declaration = PsiTreeUtil.getParentOfType(
                    previous,
                    LezerExternalTokensDeclaration::class.java, LezerExternalPropDeclaration::class.java,
                    LezerExternalPropSourceDeclaration::class.java, LezerExternalSpecializeDeclaration::class.java,
                    LezerContextDeclaration::class.java,
                ) ?: return null
                if (previous.parent !is LezerSimpleName) return null
                if (declaration is LezerExternalPropDeclaration && declaration.simpleNameList.size == 1) listOf("as", "from") else listOf("from")
            }
        }
    }

    private companion object {
        val STATEMENT_CONTEXTS = setOf(
            LezerCompletionContext.TOP_LEVEL, LezerCompletionContext.TOKENS, LezerCompletionContext.LOCAL_TOKENS,
            LezerCompletionContext.SKIP_RULES,
        )

        /** A keyword, [suffix] is inserted after it with the caret at index [caret] of the suffix. */
        private fun keyword(text: String, suffix: String = "", caret: Int = suffix.length): LookupElement =
            LookupElementBuilder.create(text)
                .withLookupString(text.removePrefix("@"))
                .withIcon(AllIcons.Nodes.Static)
                .bold()
                .withInsertHandler(suffixHandler(suffix, caret))

        private fun described(text: String, type: String, suffix: String = ""): LookupElement =
            LookupElementBuilder.create(text)
                .withLookupString(text.removePrefix("@"))
                .withTypeText(type)
                .withInsertHandler(suffixHandler(suffix, suffix.length))

        private fun suffixHandler(suffix: String, caret: Int) = InsertHandler<LookupElement> { context, _ ->
            val tail = context.tailOffset
            // Don't duplicate e.g. the `=` of `@name=` when completing an existing prop
            if (suffix.isEmpty() || suffix.isNotBlank() && context.document.charsSequence.startsWith(suffix.trim(), tail)) {
                return@InsertHandler
            }
            context.document.insertString(tail, suffix)
            context.editor.caretModel.moveToOffset(tail + caret)
        }

        val DECLARATIONS = listOf(
            keyword("@top", " "),
            keyword("@tokens", " {\n  \n}", caret = 5),
            keyword("@local tokens", " {\n  \n  @else \n}", caret = 5),
            keyword("@skip", " {  }", caret = 3),
            keyword("@precedence", " {\n  \n}", caret = 5),
            keyword("@external tokens", " "),
            keyword("@external prop", " "),
            keyword("@external propSource", " "),
            keyword("@external specialize", " {  } ", caret = 3),
            keyword("@external extend", " {  } ", caret = 3),
            keyword("@context", " "),
            keyword("@dialects", " {  }", caret = 3),
            keyword("@detectDelim"),
        )

        val TOKENS_BLOCK = listOf(
            keyword("@precedence", " {  }", caret = 3),
            keyword("@conflict", " {  }", caret = 3),
        )

        val ASSOCIATIVITY = listOf(keyword("@left"), keyword("@right"), keyword("@cut"))

        val SPECIALIZE = listOf(keyword("@specialize", "<>", caret = 1), keyword("@extend", "<>", caret = 1))

        val CHAR_CLASSES = listOf(
            described("@asciiLetter", "\$[a-zA-Z]"),
            described("@asciiLowercase", "\$[a-z]"),
            described("@asciiUppercase", "\$[A-Z]"),
            described("@digit", "\$[0-9]"),
            described("@whitespace", "whitespace"),
            described("@eof", "end of input"),
        )

        val PSEUDO_PROPS = listOf(
            described("@name", "node name", "="),
            described("@isGroup", "node group", "="),
            described("@dialect", "dialect", "="),
            described("@dynamicPrecedence", "-10 to 10", "="),
            described("@export", "export term"),
            described("@inline", "inline rule"),
        )
    }
}
