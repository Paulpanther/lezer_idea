package de.paulmethfessel.lezer.psi

import com.intellij.psi.tree.TokenSet
import de.paulmethfessel.lezer.psi.LezerTypes.*

object LezerTokenSets {
    @JvmField
    val COMMENTS = TokenSet.create(LINE_COMMENT, BLOCK_COMMENT)

    @JvmField
    val STRINGS = TokenSet.create(STRING)

    /** `@`-keywords that start or structure a declaration. */
    @JvmField
    val DECLARATION_KEYWORDS = TokenSet.create(
        AT_TOP, AT_TOKENS, AT_LOCAL, AT_ELSE, AT_SKIP, AT_PRECEDENCE, AT_EXTERNAL,
        AT_CONTEXT, AT_DIALECTS, AT_DETECT_DELIM, AT_CONFLICT,
    )

    @JvmField
    val ASSOCIATIVITY_KEYWORDS = TokenSet.create(AT_LEFT, AT_RIGHT, AT_CUT)

    @JvmField
    val SPECIALIZE_KEYWORDS = TokenSet.create(AT_SPECIALIZE, AT_EXTEND)

    /** Words that are keywords only at specific positions, identifiers everywhere else. */
    @JvmField
    val CONTEXTUAL_KEYWORDS = TokenSet.create(
        KW_TOKENS, KW_FROM, KW_AS, KW_PROP, KW_PROP_SOURCE, KW_EXTEND, KW_SPECIALIZE,
    )
}
