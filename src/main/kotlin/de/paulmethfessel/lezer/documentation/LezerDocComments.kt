package de.paulmethfessel.lezer.documentation

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.elementType
import de.paulmethfessel.lezer.psi.LezerTypes

/**
 * Lezer has no doc comment syntax, so the comments directly before a declaration are its documentation: a block comment
 * or a run of line comments, without a blank line between them and the declaration.
 */
object LezerDocComments {
    fun commentsBefore(declaration: PsiElement): List<PsiComment> {
        val comments = mutableListOf<PsiComment>()
        var element = declaration.prevSibling
        while (element != null) {
            if (element is PsiWhiteSpace) {
                if (element.text.count { it == '\n' } > 1) break
            } else if (element is PsiComment && startsLine(element)) {
                comments += element
            } else {
                break
            }
            element = element.prevSibling
        }
        return comments.reversed()
    }

    /** The comment text without comment markers, a common indent and decorative leading `*`. */
    fun text(comments: List<PsiComment>): String = comments.joinToString("\n") { comment ->
        if (comment.elementType == LezerTypes.LINE_COMMENT) {
            comment.text.removePrefix("//").removePrefix(" ")
        } else {
            val lines = comment.text.removePrefix("/*").removeSuffix("*/").lines()
                .map { it.replace(LEADING_STAR, "") }
            lines.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }.joinToString("\n").trimIndent()
        }
    }

    private val LEADING_STAR = Regex("""^\s*\*(?!/) ?""")

    /** Excludes trailing comments of the previous line, like `a { "a" } // about a`. */
    private fun startsLine(comment: PsiComment): Boolean {
        val prev = comment.prevSibling ?: return true
        return prev is PsiWhiteSpace && (prev.textContains('\n') || prev.prevSibling == null)
    }
}
