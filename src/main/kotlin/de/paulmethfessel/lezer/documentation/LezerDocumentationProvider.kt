package de.paulmethfessel.lezer.documentation

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.markdown.utils.doc.DocMarkdownToHtmlConverter
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.richcopy.HtmlSyntaxInfoUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import de.paulmethfessel.lezer.LezerLanguage
import de.paulmethfessel.lezer.psi.*

/**
 * Quick documentation for declarations (their signature, kind and the comments before them) and for keywords and other
 * built-ins (a short description with a link to the Lezer guide).
 */
class LezerDocumentationProvider : AbstractDocumentationProvider() {
    override fun getCustomDocumentationElement(editor: Editor, file: PsiFile, contextElement: PsiElement?, targetOffset: Int): PsiElement? =
        contextElement?.takeIf { it.containingFile is LezerFile && LezerBuiltinDocs.find(it) != null }

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        if (element is LezerNamedElement) return declarationDoc(element)
        return element?.let { LezerBuiltinDocs.find(it) }?.let { builtinDoc(element, it) }
    }

    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? {
        if (element !is LezerNamedElement) return null
        return "${element.kind.description} ${StringUtil.escapeXmlEntities(signature(element))}"
    }

    private fun declarationDoc(element: LezerNamedElement): String = buildString {
        append(DocumentationMarkup.DEFINITION_START)
        appendCode(element, signature(element))
        append(DocumentationMarkup.DEFINITION_END)

        val comments = LezerDocComments.commentsBefore(element)
        if (comments.isNotEmpty()) {
            append(DocumentationMarkup.CONTENT_START)
            append(DocMarkdownToHtmlConverter.convert(element.project, LezerDocComments.text(comments)))
            append(DocumentationMarkup.CONTENT_END)
        }

        append(DocumentationMarkup.SECTIONS_START)
        section("Kind", element.kind.description.replaceFirstChar { it.uppercase() })
        details(element).forEach { (header, value) -> section(header, value) }
        val (anchor, title) = GUIDE_SECTIONS.getValue(element.kind)
        section("Guide", guideLink(anchor, title))
        append(DocumentationMarkup.SECTIONS_END)
    }

    private fun builtinDoc(element: PsiElement, doc: LezerBuiltinDocs.Doc): String = buildString {
        append(DocumentationMarkup.DEFINITION_START)
        appendCode(element, doc.signature)
        append(DocumentationMarkup.DEFINITION_END)
        append(DocumentationMarkup.CONTENT_START).append(doc.description).append(DocumentationMarkup.CONTENT_END)
        append(DocumentationMarkup.SECTIONS_START)
        section("Guide", guideLink(doc.guideSection, doc.guideTitle))
        append(DocumentationMarkup.SECTIONS_END)
    }

    /** Kind specific information: the node a rule creates, the associativity of a precedence, ... */
    private fun details(element: LezerNamedElement): List<Pair<String, String>> = buildList {
        val kind = element.kind
        if (kind.isTerm || kind.isToken) add("Node" to nodeDescription(element))
        when (element) {
            is LezerParameter -> PsiTreeUtil.getParentOfType(element, LezerNamedElement::class.java)?.name
                ?.let { add("Parameter of" to code(it)) }
            is LezerPrecedence -> {
                val precedences = (element.parent as? LezerPrecedenceBody)?.precedenceList.orEmpty()
                val associativity = when (element.lastChild.elementType) {
                    LezerTypes.AT_LEFT -> "left associative"
                    LezerTypes.AT_RIGHT -> "right associative"
                    LezerTypes.AT_CUT -> "cut"
                    else -> "no associativity"
                }
                add("Precedence" to "${precedences.indexOf(element) + 1} of ${precedences.size} (highest first), $associativity")
            }
        }
        scope(element)?.let { add("Declared in" to code(it)) }
    }

    /**
     * Rules and tokens create a node if their name is capitalized or they have an `@name` prop, see
     * https://lezer.codemirror.net/docs/guide/#writing-a-grammar
     */
    private fun nodeDescription(element: LezerNamedElement): String {
        val props = PsiTreeUtil.getChildOfType(element, LezerProps::class.java)
        val explicitName = props?.propList?.firstOrNull { it.firstChild.text == "@name" }
            ?.let { prop -> prop.text.substringAfter('=', "").takeIf { it.isNotEmpty() } }
        val name = element.name.orEmpty()
        return when {
            explicitName != null -> "${code(explicitName)} (from <code>@name</code>)"
            name.firstOrNull()?.isUpperCase() == true -> code(name)
            element.kind == LezerDeclarationKind.TOP_RULE -> "${code(name)} (root node)"
            else -> "None, the name is not capitalized"
        }
    }

    /** The block a rule or token is declared in, if it is not a top-level declaration. */
    private fun scope(element: LezerNamedElement): String? {
        if (element is LezerParameter || element is LezerInlineRuleExpression) return null
        return when (element.parent) {
            is LezerTokensBody -> "@tokens"
            is LezerLocalTokensBody -> "@local tokens"
            is LezerSkipBody -> "@skip"
            is LezerExternalTokenSet -> "@external"
            else -> null
        }
    }

    /** The declaration without its body, like `expression[@isGroup=Expression]` or `@top Program`. */
    private fun signature(element: LezerNamedElement): String {
        val parts = mutableListOf<String>()
        var child = element.firstChild
        while (child != null) {
            when {
                child is PsiComment || child is PsiWhiteSpace -> {}
                // Bodies are left out, except in the middle of `@external specialize {…} name from "…"`
                child is LezerBody || child is LezerExternalTokenSet -> if (hasContentAfter(child)) parts += "{…}"
                else -> parts += child.text.replace(WHITESPACE, " ")
            }
            child = child.nextSibling
        }
        return parts.joinToString(" ").replace(" [", "[").replace(" <", "<")
    }

    private fun hasContentAfter(element: PsiElement): Boolean =
        generateSequence(element.nextSibling) { it.nextSibling }.any { it !is PsiWhiteSpace && it !is PsiComment }

    /** Appends the code highlighted by the lexer, in a `<pre>` block. */
    private fun StringBuilder.appendCode(context: PsiElement, code: String) {
        HtmlSyntaxInfoUtil.appendHighlightedByLexerAndEncodedAsHtmlCodeSnippet(this, context.project, LezerLanguage, code, 1f)
    }

    private fun StringBuilder.section(header: String, value: String) {
        append(DocumentationMarkup.SECTION_HEADER_START).append(header).append(":")
        append(DocumentationMarkup.SECTION_SEPARATOR).append(value).append(DocumentationMarkup.SECTION_END)
    }

    private fun code(text: String) = "<code>${StringUtil.escapeXmlEntities(text)}</code>"

    private fun guideLink(anchor: String, title: String) =
        """<a href="${LezerBuiltinDocs.GUIDE}#$anchor">Lezer guide: $title</a>"""

    private companion object {
        val WHITESPACE = Regex("""\s+""")

        val GUIDE_SECTIONS = mapOf(
            LezerDeclarationKind.TOP_RULE to ("writing-a-grammar" to "Writing a Grammar"),
            LezerDeclarationKind.TERM to ("writing-a-grammar" to "Writing a Grammar"),
            LezerDeclarationKind.TERM_TEMPLATE to ("template-rules" to "Template Rules"),
            LezerDeclarationKind.INLINE_TERM to ("inline-rules" to "Inline Rules"),
            LezerDeclarationKind.TOKEN to ("tokens" to "Tokens"),
            LezerDeclarationKind.TOKEN_TEMPLATE to ("template-rules" to "Template Rules"),
            LezerDeclarationKind.INLINE_TOKEN to ("inline-rules" to "Inline Rules"),
            LezerDeclarationKind.EXTERNAL_TOKEN to ("external-tokens" to "External Tokens"),
            LezerDeclarationKind.PARAMETER to ("template-rules" to "Template Rules"),
            LezerDeclarationKind.PRECEDENCE to ("precedence" to "Precedence"),
            LezerDeclarationKind.DIALECT to ("dialects" to "Dialects"),
            LezerDeclarationKind.EXTERNAL_PROP to ("node-props" to "Node Props"),
            LezerDeclarationKind.EXTERNAL_TOKENIZER to ("external-tokens" to "External Tokens"),
            LezerDeclarationKind.EXTERNAL_SPECIALIZER to ("external-tokens" to "External Tokens"),
            LezerDeclarationKind.PROP_SOURCE to ("node-props" to "Node Props"),
            LezerDeclarationKind.CONTEXT to ("context" to "Context"),
        )
    }
}
