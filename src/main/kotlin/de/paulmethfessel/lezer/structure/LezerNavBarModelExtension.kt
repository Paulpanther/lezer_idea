package de.paulmethfessel.lezer.structure

import com.intellij.ide.navigationToolbar.AbstractNavBarModelExtension
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataMap
import com.intellij.psi.PsiElement
import com.intellij.util.Processor
import de.paulmethfessel.lezer.psi.LezerFile
import javax.swing.Icon

/**
 * Shows the declaration at the caret in the navigation bar, below the file, using the same nodes as the structure
 * view. Based on [AbstractNavBarModelExtension] rather than the platform's structure-aware one, which moved to the
 * Structure View plugin in 2026.2 and doesn't exist there in the IDE versions before.
 */
class LezerNavBarModelExtension : AbstractNavBarModelExtension() {
    override fun getPresentableText(item: Any?): String? = (item as? PsiElement)?.let(LezerStructure::presentableText)

    override fun getIcon(item: Any?): Icon? = (item as? PsiElement)?.let(LezerStructure::icon)

    /** The innermost structure node at the caret. */
    override fun getLeafElement(dataContext: DataMap): PsiElement? {
        val file = dataContext[CommonDataKeys.PSI_FILE] as? LezerFile ?: return null
        val editor = dataContext[CommonDataKeys.EDITOR] ?: return null
        val offset = editor.caretModel.offset
        return node(file.findElementAt(offset) ?: file.findElementAt(offset - 1))
    }

    /** The enclosing structure node, or the file for top-level declarations. */
    override fun getParent(psiElement: PsiElement?): PsiElement? {
        if (psiElement == null || psiElement is LezerFile || psiElement.containingFile !is LezerFile) return null
        if (!LezerStructure.isNode(psiElement)) return null
        return node(psiElement.parent) ?: psiElement.containingFile
    }

    override fun processChildren(`object`: Any?, rootElement: Any?, processor: Processor<Any>): Boolean {
        val element = `object` as? PsiElement ?: return true
        if (element !is LezerFile && (element.containingFile !is LezerFile || !LezerStructure.isNode(element))) return true
        return LezerStructure.children(element).all(processor::process)
    }

    /** Keeps the source order of the declarations. */
    override fun normalizeChildren(): Boolean = false

    /** [element] or its innermost ancestor that is a structure node, excluding the file. */
    private fun node(element: PsiElement?): PsiElement? {
        var current = element
        while (current != null && current !is LezerFile) {
            if (LezerStructure.isNode(current)) return current
            current = current.parent
        }
        return null
    }
}
