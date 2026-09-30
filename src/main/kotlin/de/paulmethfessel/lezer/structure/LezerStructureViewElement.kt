package de.paulmethfessel.lezer.structure

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.SortableTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.NavigatablePsiElement

class LezerStructureViewElement(private val element: NavigatablePsiElement) : StructureViewTreeElement, SortableTreeElement {
    override fun getValue(): Any = element

    override fun navigate(requestFocus: Boolean) = element.navigate(requestFocus)

    override fun canNavigate(): Boolean = element.canNavigate()

    override fun canNavigateToSource(): Boolean = element.canNavigateToSource()

    override fun getAlphaSortKey(): String = LezerStructure.presentableText(element).orEmpty()

    override fun getPresentation(): ItemPresentation =
        PresentationData(LezerStructure.presentableText(element), null, LezerStructure.icon(element), null)

    override fun getChildren(): Array<TreeElement> =
        LezerStructure.children(element).map(::LezerStructureViewElement).toTypedArray()
}
