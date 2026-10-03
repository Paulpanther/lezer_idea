package de.paulmethfessel.lezer.generator.run

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.toolbar.floating.FloatingToolbarProvider
import de.paulmethfessel.lezer.LezerFileType

/** Shows the "regenerate on save" checkbox in the top right corner of grammar editors, like the open in browser icons. */
class GenerateOnSaveFloatingToolbarProvider : FloatingToolbarProvider {
    override val actionGroup: ActionGroup by lazy {
        DefaultActionGroup(ActionManager.getInstance().getAction("Lezer.GenerateOnSaveToggle"))
    }

    override fun isApplicable(dataContext: DataContext): Boolean {
        val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return false
        return editor.editorKind == EditorKind.MAIN_EDITOR && editor.virtualFile?.fileType == LezerFileType
    }
}
