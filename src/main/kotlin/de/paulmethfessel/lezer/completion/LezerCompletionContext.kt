package de.paulmethfessel.lezer.completion

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import de.paulmethfessel.lezer.psi.LezerTypes

/**
 * The kind of block a completion position is in. It is determined from the tokens before the position rather than the
 * PSI tree, since the incomplete keyword being typed usually breaks the structure, e.g. `@top A { @spec }` recovers
 * with `@spec` outside the rule body.
 */
enum class LezerCompletionContext {
    TOP_LEVEL,
    /** Directly in `@tokens { }`. */
    TOKENS,
    /** Directly in `@local tokens { }`. */
    LOCAL_TOKENS,
    /** The rules of `@skip { … } { rules }`. */
    SKIP_RULES,
    /** `@precedence { }` at the top level. */
    PRECEDENCE,
    /** `@external tokens … { }`. */
    EXTERNAL_TOKEN_SET,
    /** Other lists like token precedences, conflicts and dialects. */
    LIST,
    /** The body of a rule, or another expression outside of tokens. */
    RULE,
    /** The body of a token rule. */
    TOKEN_RULE,
    /** Between `[` and `]`. */
    PROPS;

    companion object {
        fun of(position: PsiElement): LezerCompletionContext {
            var braces = 0
            var brackets = 0
            var leaf = PsiTreeUtil.prevLeaf(position)
            while (leaf != null) {
                when (leaf.elementType) {
                    LezerTypes.RBRACE -> braces++
                    LezerTypes.RBRACKET -> brackets++
                    LezerTypes.LBRACKET -> if (brackets == 0 && braces == 0) return PROPS else brackets--
                    LezerTypes.LBRACE -> if (braces == 0) return ofBlock(leaf) else braces--
                }
                leaf = PsiTreeUtil.prevLeaf(leaf)
            }
            return TOP_LEVEL
        }

        /** What the block opened by [lbrace] is, by the tokens before it. */
        private fun ofBlock(lbrace: PsiElement): LezerCompletionContext {
            val previous = previousToken(lbrace)
            return when (previous?.elementType) {
                LezerTypes.AT_TOKENS -> TOKENS
                LezerTypes.KW_TOKENS -> if (previousToken(previous)?.elementType == LezerTypes.AT_LOCAL) LOCAL_TOKENS else RULE
                LezerTypes.AT_PRECEDENCE -> if (of(previous) in TOKEN_BLOCKS) LIST else PRECEDENCE
                LezerTypes.AT_CONFLICT, LezerTypes.AT_DIALECTS -> LIST
                // `@external tokens name from "./module" {`
                LezerTypes.STRING -> EXTERNAL_TOKEN_SET
                // The second block of `@skip { … } { … }`
                LezerTypes.RBRACE -> SKIP_RULES
                LezerTypes.AT_SKIP -> RULE
                else -> when (of(lbrace)) {
                    TOKENS, LOCAL_TOKENS, TOKEN_RULE -> TOKEN_RULE
                    else -> RULE
                }
            }
        }

        private val TOKEN_BLOCKS = setOf(TOKENS, LOCAL_TOKENS)

        private fun previousToken(element: PsiElement): PsiElement? {
            var leaf = PsiTreeUtil.prevLeaf(element)
            while (leaf is PsiWhiteSpace || leaf is PsiComment) leaf = PsiTreeUtil.prevLeaf(leaf)
            return leaf
        }
    }
}
