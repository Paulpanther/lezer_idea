package de.paulmethfessel.lezer.playground

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory
import de.paulmethfessel.lezer.LezerFileType

class LezerPlaygroundToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = LezerPlaygroundPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, null, false)
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)
    }

    companion object {
        const val ID = "Lezer Playground"

        fun panel(project: Project): LezerPlaygroundPanel? =
            ToolWindowManager.getInstance(project).getToolWindow(ID)?.contentManager?.contents
                ?.firstNotNullOfOrNull { it.component as? LezerPlaygroundPanel }
    }
}

/** Opens the playground with the grammar of the editor. */
class OpenInLezerPlaygroundAction : DumbAwareAction() {
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null && e.getData(CommonDataKeys.VIRTUAL_FILE)?.fileType == LezerFileType
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(LezerPlaygroundToolWindowFactory.ID) ?: return
        toolWindow.activate {
            LezerPlaygroundToolWindowFactory.panel(project)?.showGrammar(file)
        }
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

/**
 * Restarts the worker when a JS/TS file is saved, since it may be imported by the grammar (e.g. an external tokenizer)
 * and node caches the modules that it imports.
 */
class PlaygroundModuleListener(private val project: Project) : BulkFileListener {
    override fun after(events: List<VFileEvent>) {
        val modulesChanged = events.any { it is VFileContentChangeEvent && it.file.extension in MODULE_EXTENSIONS }
        if (!modulesChanged) return
        val panel = LezerPlaygroundToolWindowFactory.panel(project) ?: return
        PlaygroundWorker.getInstance(project).restart()
        panel.scheduleParse(0)
    }

    private companion object {
        val MODULE_EXTENSIONS = setOf("js", "mjs", "cjs", "ts", "mts", "cts")
    }
}
