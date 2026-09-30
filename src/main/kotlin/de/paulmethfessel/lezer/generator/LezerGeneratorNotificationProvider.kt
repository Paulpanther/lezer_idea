package de.paulmethfessel.lezer.generator

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import de.paulmethfessel.lezer.LezerFileType
import java.util.function.Function
import javax.swing.JComponent

/** Offers to install `@lezer/generator` in grammar files if it is missing. */
class LezerGeneratorNotificationProvider : EditorNotificationProvider, DumbAware {
    override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
        if (file.fileType != LezerFileType) return null
        if (LezerSettings.getInstance(project).state.bannerDismissed) return null

        if (NodeLocator.find(project) == null) {
            return Function { editor ->
                panel(editor, project, "Node.js was not found. It is needed to check grammars and generate parsers with lezer-generator.")
            }
        }
        if (GeneratorLocator.resolve(project, file) != null) return null

        return Function { editor ->
            panel(editor, project, "@lezer/generator is not installed. Grammars are only checked for syntax errors and parsers cannot be generated.").apply {
                createActionLabel("Install locally (npm i -D)") { GeneratorInstaller.install(project, file, global = false) }
                createActionLabel("Install globally") { GeneratorInstaller.install(project, file, global = true) }
            }
        }
    }

    private fun panel(editor: FileEditor, project: Project, text: String) =
        EditorNotificationPanel(editor, EditorNotificationPanel.Status.Warning).apply {
            this.text = text
            createActionLabel("Configure…") {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, LezerConfigurable::class.java)
            }
            setCloseAction {
                LezerSettings.getInstance(project).state.bannerDismissed = true
                EditorNotifications.getInstance(project).updateAllNotifications()
            }
        }
}
