package de.paulmethfessel.lezer.playground

import com.intellij.execution.RunManager
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
import de.paulmethfessel.lezer.generator.run.LezerGeneratorConfigurationType
import de.paulmethfessel.lezer.generator.run.LezerGeneratorRunConfiguration
import java.nio.file.Path
import kotlin.io.path.Path

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
 * Restarts the worker when a module it may have imported changed, since node caches the modules that it imports: an
 * `@external` implementation, or another project file it could import. Dependencies in `node_modules` and the parsers
 * written by the generator are left out, they change on every install and generation.
 */
class PlaygroundModuleListener(private val project: Project) : BulkFileListener {
    override fun after(events: List<VFileEvent>) {
        val changed = events.filterIsInstance<VFileContentChangeEvent>().map { it.file }
            .filter { it.extension in MODULE_EXTENSIONS }
        if (changed.isEmpty()) return
        val panel = LezerPlaygroundToolWindowFactory.panel(project) ?: return
        val loaded = panel.result?.modules.orEmpty().map { Path(it) }.toSet()
        if (loaded.isEmpty()) return
        val outputs = RunManager.getInstance(project).getConfigurationsList(LezerGeneratorConfigurationType.getInstance())
            .filterIsInstance<LezerGeneratorRunConfiguration>()
            .flatMap { it.outputPaths() }
            .toSet()
        if (changed.none { file -> file.fileSystem.getNioPath(file)?.let { needsRestart(it, loaded, outputs) } == true }) return
        PlaygroundWorker.getInstance(project).restart()
        panel.scheduleParse(0)
    }

    companion object {
        private val MODULE_EXTENSIONS = setOf("js", "mjs", "cjs", "ts", "mts", "cts")

        /** Whether a change of [file] may affect the worker that imported the modules [loaded]. */
        fun needsRestart(file: Path, loaded: Set<Path>, generatorOutputs: Set<Path>): Boolean = when {
            file in loaded -> true
            file in generatorOutputs -> false
            else -> file.none { it.toString() == "node_modules" }
        }
    }
}
