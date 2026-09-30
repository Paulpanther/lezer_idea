package de.paulmethfessel.lezer.annotator

import com.intellij.codeInsight.highlighting.HighlightErrorFilter
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.TokenType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import de.paulmethfessel.lezer.psi.LezerFile
import de.paulmethfessel.lezer.psi.LezerTypes

/**
 * Annotates simple syntax errors with readable messages.
 * Replaces the default highlighting of parser errors, see [LezerHighlightErrorFilter].
 */
class LezerErrorAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        when {
            element is PsiErrorElement -> annotateParserError(element, holder)
            element.firstChild == null && element !is PsiWhiteSpace -> {
                annotateUnexpectedToken(element, holder)
                annotateToken(element, holder)
            }
            else -> annotateBadCharacters(element, holder)
        }
    }

    /** Bad characters don't belong to the Lezer language, so they are annotated when visiting their parent. */
    private fun annotateBadCharacters(element: PsiElement, holder: AnnotationHolder) {
        for (child in element.node.getChildren(BAD_CHARACTERS)) {
            holder.newAnnotation(HighlightSeverity.ERROR, "Unexpected character '${child.text}'").range(child).create()
        }
    }

    private fun annotateToken(element: PsiElement, holder: AnnotationHolder) {
        when {
            element.elementType == LezerTypes.STRING && !isTerminated(element.text, element.text.first()) ->
                holder.newAnnotation(HighlightSeverity.ERROR, "Unterminated string literal").create()
            (element.elementType == LezerTypes.CHAR_SET || element.elementType == LezerTypes.INVERTED_CHAR_SET) &&
                !isTerminated(element.text.drop(1), ']') ->
                holder.newAnnotation(HighlightSeverity.ERROR, "Unterminated character set").create()
            element.elementType == LezerTypes.BLOCK_COMMENT && (element.textLength < 4 || !element.text.endsWith("*/")) ->
                holder.newAnnotation(HighlightSeverity.ERROR, "Unterminated block comment").create()
        }
    }

    private fun annotateParserError(error: PsiErrorElement, holder: AnnotationHolder) {
        // Bad characters are reported by themselves
        if (PsiTreeUtil.firstChild(error).elementType == TokenType.BAD_CHARACTER) {
            annotateBadCharacters(error, holder)
            return
        }
        when {
            error.textLength > 0 -> holder.newAnnotation(HighlightSeverity.ERROR, message(error)).range(error).create()
            // Reported at the unexpected token when the token is visited
            unexpectedTokenAfter(error) != null -> {}
            else -> holder.newAnnotation(HighlightSeverity.ERROR, message(error))
                .range(TextRange.from(error.textOffset, 0))
                .afterEndOfLine()
                .create()
        }
    }

    /** Empty error elements are placed directly after the last valid token, highlight the unexpected token instead. */
    private fun annotateUnexpectedToken(token: PsiElement, holder: AnnotationHolder) {
        if (token.elementType == TokenType.BAD_CHARACTER) return
        val error = emptyErrorBefore(token) ?: return
        holder.newAnnotation(HighlightSeverity.ERROR, message(error)).range(token).create()
    }

    private fun message(error: PsiErrorElement): String = LezerErrorMessages.simplify(error.errorDescription)

    /** The token following the empty [error] on the same line. */
    private fun unexpectedTokenAfter(error: PsiErrorElement): PsiElement? {
        var next = PsiTreeUtil.nextLeaf(error) ?: return null
        if (next is PsiWhiteSpace) {
            if (next.textContains('\n')) return null
            next = PsiTreeUtil.nextLeaf(next) ?: return null
        }
        return next.takeIf { it !is PsiErrorElement }
    }

    /** The empty error element preceding [token] on the same line. */
    private fun emptyErrorBefore(token: PsiElement): PsiErrorElement? {
        var prev = PsiTreeUtil.prevLeaf(token) ?: return null
        if (prev is PsiWhiteSpace) {
            if (prev.textContains('\n')) return null
            prev = PsiTreeUtil.prevLeaf(prev) ?: return null
        }
        return (prev as? PsiErrorElement)?.takeIf { it.textLength == 0 }
    }

    private companion object {
        val BAD_CHARACTERS = TokenSet.create(TokenType.BAD_CHARACTER)
    }

    /** Whether [text] (starting after the opening delimiter) ends with an unescaped [closer]. */
    private fun isTerminated(text: String, closer: Char): Boolean {
        if (text.length < 2 || text.last() != closer) return false
        val backslashes = text.dropLast(1).takeLastWhile { it == '\\' }.length
        return backslashes % 2 == 0
    }
}

/** Disables the default highlighting of parser errors in Lezer files, [LezerErrorAnnotator] reports them instead. */
class LezerHighlightErrorFilter : HighlightErrorFilter() {
    override fun shouldHighlightErrorElement(element: PsiErrorElement): Boolean = element.containingFile !is LezerFile
}
