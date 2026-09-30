package de.paulmethfessel.lezer.documentation

import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import de.paulmethfessel.lezer.psi.*
import de.paulmethfessel.lezer.resolve.LezerResolver

/** Documentation of Lezer's keywords, built-in character classes and props, with links into the Lezer guide. */
object LezerBuiltinDocs {
    const val GUIDE = "https://lezer.codemirror.net/docs/guide/"

    class Doc(val signature: String, val description: String, val guideSection: String, val guideTitle: String) {
        val guideUrl: String get() = GUIDE + "#" + guideSection
    }

    /** The documentation for the keyword or built-in name [element] (a leaf), if it is one. */
    fun find(element: PsiElement): Doc? {
        val parent = element.parent
        return when (element.elementType) {
            LezerTypes.AT_TOP -> TOP
            LezerTypes.AT_TOKENS -> TOKENS
            LezerTypes.AT_LOCAL -> LOCAL_TOKENS
            LezerTypes.AT_ELSE -> ELSE
            LezerTypes.AT_SKIP -> SKIP
            LezerTypes.AT_PRECEDENCE -> if (parent is LezerTokenPrecedenceDeclaration) TOKEN_PRECEDENCE else PRECEDENCE
            LezerTypes.AT_LEFT -> LEFT
            LezerTypes.AT_RIGHT -> RIGHT
            LezerTypes.AT_CUT -> CUT
            LezerTypes.AT_CONFLICT -> if (parent is LezerExternalConflictDeclaration) EXTERNAL_CONFLICT else TOKEN_CONFLICT
            LezerTypes.AT_EXTERNAL -> external(parent)
            LezerTypes.AT_CONTEXT -> CONTEXT
            LezerTypes.AT_DIALECTS -> DIALECTS
            LezerTypes.AT_DETECT_DELIM -> DETECT_DELIM
            LezerTypes.AT_SPECIALIZE -> if (parent is LezerSpecializeExpression) SPECIALIZE else null
            LezerTypes.AT_EXTEND -> if (parent is LezerSpecializeExpression) EXTEND else null
            LezerTypes.CHAR_CLASS -> CHAR_CLASSES[element.text]
            LezerTypes.ANY_CHAR -> ANY_CHAR
            LezerTypes.CHAR_SET -> CHAR_SET
            LezerTypes.INVERTED_CHAR_SET -> INVERTED_CHAR_SET
            LezerTypes.BANG -> if (parent is LezerPrecedenceMarker) PRECEDENCE_MARKER else null
            LezerTypes.TILDE -> if (parent is LezerAmbiguityMarker) AMBIGUITY_MARKER else null
            LezerTypes.AT_NAME -> if (parent is LezerProp) PSEUDO_PROPS[element.text] ?: UNKNOWN_PSEUDO_PROP else null
            // `tokens`, `from`, ... document the declaration they belong to, as identifiers they are names
            in LezerTokenSets.CONTEXTUAL_KEYWORDS -> when (parent) {
                is LezerLocalTokensDeclaration -> LOCAL_TOKENS
                is LezerExternalTokensDeclaration, is LezerExternalPropDeclaration, is LezerExternalPropSourceDeclaration,
                is LezerExternalSpecializeDeclaration, is LezerContextDeclaration -> external(parent)
                is LezerPropName -> builtinProp(parent)
                else -> null
            }
            in LezerTokenSets.IDENTIFIERS -> (parent as? LezerPropName)?.let(::builtinProp)
            else -> null
        }
    }

    /** Props like `closedBy` are predefined unless an `@external prop` with that name is declared. */
    private fun builtinProp(name: LezerPropName): Doc? {
        if (LezerResolver.resolve(name) != null) return null
        return BUILTIN_PROPS[name.text]
    }

    private fun external(declaration: PsiElement?): Doc? = when (declaration) {
        is LezerExternalTokensDeclaration -> EXTERNAL_TOKENS
        is LezerExternalPropDeclaration -> EXTERNAL_PROP
        is LezerExternalPropSourceDeclaration -> EXTERNAL_PROP_SOURCE
        is LezerExternalSpecializeDeclaration -> EXTERNAL_SPECIALIZE
        is LezerContextDeclaration -> CONTEXT
        else -> null
    }

    private val TOP = Doc(
        "@top Name { expression }",
        "Declares a top rule, the entry point of the grammar that becomes the root node of the tree. " +
            "A grammar can have several top rules: the first one is used by default, others can be selected with the " +
            "parser's <code>top</code> configuration option.",
        "writing-a-grammar", "Writing a Grammar",
    )
    private val TOKENS = Doc(
        "@tokens { … }",
        "Declares tokens, the terminals that the tokenizer reads directly from the input. Token rules can only " +
            "refer to other tokens. Literal tokens listed here (like <code>\"(\"</code>) can be given props and then " +
            "create nodes. Tokens with capitalized names create nodes.",
        "tokens", "Tokens",
    )
    private val LOCAL_TOKENS = Doc(
        "@local tokens { … @else fallback }",
        "Declares a local token group: tokens that are only active in specific places, like the content of a " +
            "string. Everything that none of its tokens match becomes the <code>@else</code> fallback token. " +
            "Only tokens of the group may occur where it is used.",
        "local-token-groups", "Local Token Groups",
    )
    private val ELSE = Doc(
        "@else name",
        "The fallback token of a local token group, it matches any input the group's other tokens don't match.",
        "local-token-groups", "Local Token Groups",
    )
    private val SKIP = Doc(
        "@skip { expression } { rules }",
        "Declares what can appear between any two tokens, typically whitespace and comments. Without a second " +
            "block it applies to the whole grammar, with a second block only to the rules declared inside it. " +
            "<code>@skip {} { … }</code> disables skipping, e.g. for the content of strings.",
        "skip-expressions", "Skip Expressions",
    )
    private val PRECEDENCE = Doc(
        "@precedence { name @left, … }",
        "Declares named precedences, from the highest to the lowest. They are used with <code>!name</code> markers " +
            "to resolve ambiguities like operator priorities. Each precedence can be <code>@left</code> or " +
            "<code>@right</code> associative, or a <code>@cut</code>.",
        "precedence", "Precedence",
    )
    private val TOKEN_PRECEDENCE = Doc(
        "@precedence { token, … }",
        "Token precedence inside <code>@tokens</code>: if several of the listed tokens (or literals) match at the " +
            "same position, the one that comes first wins, e.g. keywords over identifiers.",
        "token-precedence", "Token Precedence",
    )
    private val LEFT = Doc(
        "name @left",
        "Makes the precedence left-associative: <code>a + b + c</code> is parsed as <code>(a + b) + c</code>.",
        "precedence", "Precedence",
    )
    private val RIGHT = Doc(
        "name @right",
        "Makes the precedence right-associative: <code>a = b = c</code> is parsed as <code>a = (b = c)</code>.",
        "precedence", "Precedence",
    )
    private val CUT = Doc(
        "name @cut",
        "Makes the precedence a cut operator: where its marker occurs, it overrides other interpretations even if " +
            "no conflict was detected yet, e.g. to parse <code>function</code> at the start of a statement as a " +
            "declaration.",
        "precedence", "Precedence",
    )
    private val TOKEN_CONFLICT = Doc(
        "@conflict { token, token }",
        "Explicitly declares the tokens as conflicting, as if they could match the same input, so the generator " +
            "makes sure they are never used in the same context.",
        "token-precedence", "Token Precedence",
    )
    private val EXTERNAL_CONFLICT = Doc(
        "@conflict { token }",
        "Makes sure the tokens of this external tokenizer are never used together with the listed tokens.",
        "external-tokens", "External Tokens",
    )
    private val EXTERNAL_TOKENS = Doc(
        "@external tokens tokenizer from \"./module\" { Token, … }",
        "Declares tokens that are read by an <code>ExternalTokenizer</code> exported as <code>tokenizer</code> " +
            "from the module. Its position relative to <code>@tokens</code> determines which is tried first.",
        "external-tokens", "External Tokens",
    )
    private val EXTERNAL_PROP = Doc(
        "@external prop name as alias from \"./module\"",
        "Imports a <code>NodeProp</code> from the module, so it can be used in props like <code>Rule[name=value]</code>. " +
            "With <code>as</code> it is used under another name in the grammar.",
        "node-props", "Node Props",
    )
    private val EXTERNAL_PROP_SOURCE = Doc(
        "@external propSource name from \"./module\"",
        "Adds the <code>NodePropSource</code> exported from the module to the parser, e.g. the highlighting styles " +
            "created with <code>styleTags</code>.",
        "node-props", "Node Props",
    )
    private val EXTERNAL_SPECIALIZE = Doc(
        "@external specialize { token } function from \"./module\" { Token, … }",
        "Calls the function exported from the module for every token matched by the first block, it can replace " +
            "the token with one of the listed ones. With <code>@external extend</code>, both the original and the new " +
            "token are tried.",
        "external-tokens", "External Tokens",
    )
    private val CONTEXT = Doc(
        "@context tracker from \"./module\"",
        "Enables context tracking with the <code>ContextTracker</code> exported from the module, which maintains a " +
            "value (like the current indentation) while parsing that external tokenizers can use.",
        "context", "Context",
    )
    private val DIALECTS = Doc(
        "@dialects { name, … }",
        "Declares the dialects of the grammar. Tokens marked with <code>[@dialect=name]</code> are only recognized " +
            "when the dialect is enabled with the parser's <code>dialect</code> option.",
        "dialects", "Dialects",
    )
    private val DETECT_DELIM = Doc(
        "@detectDelim",
        "Automatically detects delimiter tokens of rules, like brackets, and adds <code>openedBy</code> and " +
            "<code>closedBy</code> props for them.",
        "node-props", "Node Props",
    )
    private val SPECIALIZE = Doc(
        "@specialize[props]<token, \"value\">",
        "Specializes a token: where <code>token</code> matches exactly <code>value</code>, a separate token is " +
            "produced instead, typically for keywords that would also match as identifiers.",
        "token-specialization", "Token Specialization",
    )
    private val EXTEND = Doc(
        "@extend[props]<token, \"value\">",
        "Like <code>@specialize</code>, but both the original token and the specialized one are tried (using GLR " +
            "parsing), for contextual keywords that can also be identifiers.",
        "token-specialization", "Token Specialization",
    )
    private val ANY_CHAR = Doc("_", "Matches any single character. Only valid in tokens.", "tokens", "Tokens")
    private val CHAR_SET = Doc(
        "\$[a-z_]",
        "Character set: matches one character from the set. Ranges like <code>a-z</code> are allowed, special " +
            "characters are escaped with a backslash.",
        "tokens", "Tokens",
    )
    private val INVERTED_CHAR_SET = Doc(
        "![\\n\"]",
        "Inverted character set: matches any character that is not in the set, e.g. everything up to the end " +
            "of the line.",
        "tokens", "Tokens",
    )
    private val PRECEDENCE_MARKER = Doc(
        "!name",
        "Precedence marker: gives the position the precedence <code>name</code> declared in <code>@precedence</code>, " +
            "resolving conflicts in favor of the higher precedence.",
        "precedence", "Precedence",
    )
    private val AMBIGUITY_MARKER = Doc(
        "~name",
        "Ambiguity marker: allows the conflict between positions with the same marker. The parser then tries " +
            "all alternatives in parallel (GLR) and keeps the ones that don't fail.",
        "allowing-ambiguity", "Allowing Ambiguity",
    )

    private val CHAR_CLASSES = mapOf(
        "@asciiLetter" to "Matches an ASCII letter, like <code>\$[a-zA-Z]</code>.",
        "@asciiLowercase" to "Matches a lowercase ASCII letter, like <code>\$[a-z]</code>.",
        "@asciiUppercase" to "Matches an uppercase ASCII letter, like <code>\$[A-Z]</code>.",
        "@digit" to "Matches an ASCII digit, like <code>\$[0-9]</code>.",
        "@whitespace" to "Matches any character the Unicode standard defines as whitespace.",
        "@eof" to "Matches the end of the input.",
    ).mapValues { (name, description) -> Doc(name, description, "tokens", "Tokens") }

    private val PSEUDO_PROPS = mapOf(
        "@name" to Doc(
            "Rule[@name=Name]",
            "Sets the name of the node the rule creates. The node is created even if the rule's name is lowercase. " +
                "Can use parameters, like <code>@name={param}</code>.",
            "node-props", "Node Props",
        ),
        "@isGroup" to Doc(
            "rule[@isGroup=Group]",
            "Adds the group to all named nodes that the rule's alternatives produce, so they can be matched by the " +
                "group name, e.g. in highlighting.",
            "node-groups", "Node Groups",
        ),
        "@dialect" to Doc(
            "Token[@dialect=name]",
            "Only recognizes the token if the dialect (declared in <code>@dialects</code>) is enabled.",
            "dialects", "Dialects",
        ),
        "@export" to Doc(
            "rule[@export] or rule[@export=name]",
            "Exports the term's ID from the generated terms file, also for rules that don't create a node.",
            "building-a-grammar", "Building a Grammar",
        ),
        "@dynamicPrecedence" to Doc(
            "Rule[@dynamicPrecedence=1]",
            "Prefers (positive values) or penalizes (negative values) the rule when the GLR parser has to choose " +
                "between ambiguous parses. Must be an integer between -10 and 10.",
            "allowing-ambiguity", "Allowing Ambiguity",
        ),
        "@inline" to Doc(
            "rule[@inline]",
            "Inlines the rule into the rules that use it. Only valid on nonterminals and doesn't take a value.",
            "inline-rules", "Inline Rules",
        ),
    )
    private val UNKNOWN_PSEUDO_PROP = Doc(
        "[@prop]",
        "Pseudo-props start with <code>@</code> and configure the generator. The known ones are " +
            PSEUDO_PROPS.keys.joinToString { "<code>$it</code>" } + ".",
        "node-props", "Node Props",
    )

    private val BUILTIN_PROPS = mapOf(
        "closedBy" to "Names of the nodes that close this node, e.g. <code>OpenBrace[closedBy=CloseBrace]</code>. Used for bracket matching.",
        "openedBy" to "Names of the nodes that open this node, e.g. <code>CloseBrace[openedBy=OpenBrace]</code>. Used for bracket matching.",
        "group" to "Names of the groups this node belongs to, separated by spaces.",
        "isolate" to "Marks the node as isolated for bidirectional text (<code>ltr</code>, <code>rtl</code> or <code>auto</code>).",
    ).filterKeys { it in LezerResolver.BUILTIN_PROPS }
        .mapValues { (name, description) ->
            Doc("Rule[$name=value]", "$description Predefined as <code>NodeProp.$name</code> in @lezer/common.", "node-props", "Node Props")
        }
}
