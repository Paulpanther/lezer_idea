package de.paulmethfessel.lezer.generator

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Panel
import com.intellij.util.ui.UIUtil
import javax.swing.event.DocumentEvent

/**
 * Selects the `@lezer/generator` installation, shows which one is found and offers to install it. Used by the settings
 * page and the run configuration, where [projectDefault] adds the option to use the settings' choice.
 */
class GeneratorSelector(
    private val project: Project,
    private val projectDefault: Boolean,
    private val grammarFile: () -> VirtualFile?,
) {
    /** A choice in the combo box, `null` stands for the project's setting. */
    private val modeCombo = ComboBox<GeneratorMode?>(
        CollectionComboBoxModel((if (projectDefault) listOf(null) else emptyList()) + GeneratorMode.entries),
    ).apply {
        renderer = SimpleListCellRenderer.create("Project default") { it?.displayName ?: "Project default" }
        addActionListener { updateState() }
    }

    private val customPath = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(project, FileChooserDescriptorFactory.createSingleFolderDescriptor().withTitle("Select @lezer/generator Package Directory"))
        textField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) = updateStatus()
        })
    }

    private val status = JBLabel().apply { foreground = UIUtil.getContextHelpForeground() }

    var mode: GeneratorMode?
        get() = modeCombo.item
        set(value) {
            modeCombo.item = if (value == null && !projectDefault) GeneratorMode.AUTO else value
            updateState()
        }

    var customGeneratorPath: String
        get() = customPath.text
        set(value) {
            customPath.text = value
        }

    fun addTo(panel: Panel) = with(panel) {
        row("Generator:") { cell(modeCombo) }
        row("Package directory:") { cell(customPath).align(AlignX.FILL) }
        row("") { cell(status) }
        row("") {
            button("Install Locally (npm i -D)") { install(global = false) }
            button("Install Globally (npm i -g)") { install(global = true) }
        }
    }

    private fun install(global: Boolean) {
        GeneratorInstaller.install(project, grammarFile(), global) { updateStatus() }
    }

    private fun updateState() {
        customPath.isEnabled = modeCombo.item == GeneratorMode.CUSTOM
        updateStatus()
    }

    /** Resolving may run npm, so it happens in the background. */
    private fun updateStatus() {
        val choice = modeCombo.item
        val path = customPath.text
        val file = grammarFile()
        status.text = "Searching…"
        ApplicationManager.getApplication().executeOnPooledThread {
            val text = when {
                NodeLocator.find(project) == null -> "Node.js was not found"
                else -> {
                    val generator = if (choice == null) GeneratorLocator.resolve(project, file)
                    else GeneratorLocator.resolve(project, file, choice, path)
                    generator?.presentableText ?: "@lezer/generator was not found"
                }
            }
            ApplicationManager.getApplication().invokeLater({
                if (modeCombo.item == choice && customPath.text == path) status.text = text
            }, ModalityState.any())
        }
    }
}
