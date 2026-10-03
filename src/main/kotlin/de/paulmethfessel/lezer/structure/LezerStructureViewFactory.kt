package de.paulmethfessel.lezer.structure

import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewModelBase
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.ide.util.treeView.smartTree.Sorter
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import de.paulmethfessel.lezer.psi.LezerFile
import de.paulmethfessel.lezer.psi.LezerNamedElement

class LezerStructureViewFactory : PsiStructureViewFactory {
    override fun getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder? {
        if (psiFile !is LezerFile) return null
        return object : TreeBasedStructureViewBuilder() {
            override fun createStructureViewModel(editor: Editor?): StructureViewModel = LezerStructureViewModel(psiFile, editor)
        }
    }
}

class LezerStructureViewModel(file: LezerFile, editor: Editor?) :
    StructureViewModelBase(file, editor, LezerStructureViewElement(file)) {

    init {
        withSorters(Sorter.ALPHA_SORTER)
        withSuitableClasses(LezerNamedElement::class.java, *LezerStructure.BLOCK_CLASSES)
    }

    override fun isSuitable(element: PsiElement?): Boolean = element != null && super.isSuitable(element) && LezerStructure.isNode(element)
}
