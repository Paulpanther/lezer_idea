package de.paulmethfessel.lezer.editor

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.CustomFoldingBuilder
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import de.paulmethfessel.lezer.psi.LezerTokenSets
import de.paulmethfessel.lezer.psi.LezerTypes.*

/** Folds multi-line braced blocks, block comments and runs of line comments, plus `// region` custom folding. */
class LezerFoldingBuilder : CustomFoldingBuilder(), DumbAware {
    override fun buildLanguageFoldRegions(
        descriptors: MutableList<FoldingDescriptor>,
        root: PsiElement,
        document: Document,
        quick: Boolean,
    ) {
        PsiTreeUtil.processElements(root) { element ->
            when {
                element.elementType in LezerTokenSets.BRACED_BLOCKS -> foldBlock(element, document, descriptors)
                element.elementType == BLOCK_COMMENT -> foldIfMultiline(element.node, element.textRange, document, descriptors)
                element.elementType == LINE_COMMENT -> foldLineComments(element as PsiComment, document, descriptors)
            }
            true
        }
    }

    override fun getLanguagePlaceholderText(node: ASTNode, range: TextRange): String = when (node.elementType) {
        BLOCK_COMMENT -> "/*...*/"
        LINE_COMMENT -> "//..."
        else -> "{...}"
    }

    override fun isRegionCollapsedByDefault(node: ASTNode): Boolean = false

    override fun isCustomFoldingCandidate(node: ASTNode): Boolean = node.elementType == LINE_COMMENT

    override fun isCustomFoldingRoot(node: ASTNode): Boolean = node.treeParent == null || node.elementType in LezerTokenSets.BRACED_BLOCKS

    /** Folds from the opening to the closing brace, incomplete blocks without a closing brace are not folded. */
    private fun foldBlock(element: PsiElement, document: Document, descriptors: MutableList<FoldingDescriptor>) {
        val node = element.node
        val lbrace = node.findChildByType(LBRACE) ?: return
        val rbrace = node.findChildByType(RBRACE) ?: return
        foldIfMultiline(node, TextRange(lbrace.startOffset, rbrace.startOffset + 1), document, descriptors)
    }

    /** Folds a run of at least two line comments that are only separated by line breaks, starting at its first one. */
    private fun foldLineComments(comment: PsiComment, document: Document, descriptors: MutableList<FoldingDescriptor>) {
        if (isCustomRegionElement(comment)) return
        if (adjacentLineComment(comment, next = false) != null) return

        var last: PsiComment = comment
        while (true) last = adjacentLineComment(last, next = true) ?: break
        if (last != comment) {
            foldIfMultiline(comment.node, TextRange(comment.textRange.startOffset, last.textRange.endOffset), document, descriptors)
        }
    }

    /** The line comment directly on the previous or next line, if it is not a custom region marker. */
    private fun adjacentLineComment(comment: PsiComment, next: Boolean): PsiComment? {
        val whitespace = (if (next) comment.nextSibling else comment.prevSibling) as? PsiWhiteSpace ?: return null
        if (whitespace.text.count { it == '\n' } != 1) return null
        val sibling = (if (next) whitespace.nextSibling else whitespace.prevSibling) as? PsiComment ?: return null
        return sibling.takeIf { it.elementType == LINE_COMMENT && !isCustomRegionElement(it) }
    }

    private fun foldIfMultiline(node: ASTNode, range: TextRange, document: Document, descriptors: MutableList<FoldingDescriptor>) {
        if (document.getLineNumber(range.startOffset) != document.getLineNumber(range.endOffset)) {
            descriptors += FoldingDescriptor(node, range)
        }
    }
}
