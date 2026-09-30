package de.paulmethfessel.lezer.generator.run

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import de.paulmethfessel.lezer.generator.GeneratorSelector
import javax.swing.JComponent
import kotlin.io.path.Path

class LezerGeneratorSettingsEditor(private val project: Project) : SettingsEditor<LezerGeneratorRunConfiguration>() {
    private val grammarFile = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory.createSingleFileDescriptor("grammar").withTitle("Select Grammar File"),
        )
    }
    private val outputFile = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor().withTitle("Select Output File"),
        )
    }
    private val workingDirectory = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor().withTitle("Select Working Directory"),
        )
    }
    private val cjs = JBCheckBox("CommonJS module (--cjs)")
    private val typeScript = JBCheckBox("TypeScript (--typeScript)")
    private val includeNames = JBCheckBox("Include node names for debugging (--names)")
    private val noTerms = JBCheckBox("Don't write the terms file (--noTerms)")
    private val exportName = JBTextField()
    private val generateOnSave = JBCheckBox("Regenerate in the background when the grammar is saved")

    private val generator = GeneratorSelector(project, projectDefault = true) {
        grammarFile.text.takeIf { it.isNotBlank() }?.let { LocalFileSystem.getInstance().findFileByNioFile(Path(it)) }
    }

    override fun createEditor(): JComponent = panel {
        row("Grammar file:") { cell(grammarFile).align(AlignX.FILL) }
        row("Output file:") {
            cell(outputFile).align(AlignX.FILL)
                .comment("--output: writes <name>.js and <name>.terms.js. Leave empty to print the parser to the console.")
        }
        row("Export name:") { cell(exportName).comment("--export: name of the exported parser, <code>parser</code> by default") }
        row { cell(cjs) }
        row { cell(typeScript) }
        row { cell(includeNames) }
        row { cell(noTerms) }
        row("Working directory:") {
            cell(workingDirectory).align(AlignX.FILL).comment("Relative paths are resolved against it. Defaults to the grammar's directory.")
        }
        row { cell(generateOnSave).comment("Errors are shown as notifications, the output doesn't open the Run tool window.") }
        group("lezer-generator") { generator.addTo(this) }
    }

    override fun resetEditorFrom(configuration: LezerGeneratorRunConfiguration) {
        val options = configuration.options
        grammarFile.text = options.grammarFile.orEmpty()
        outputFile.text = options.outputFile.orEmpty()
        workingDirectory.text = options.workingDirectory.orEmpty()
        cjs.isSelected = options.cjs
        generateOnSave.isSelected = options.generateOnSave
        typeScript.isSelected = options.typeScript
        includeNames.isSelected = options.includeNames
        noTerms.isSelected = options.noTerms
        exportName.text = options.exportName.orEmpty()
        generator.customGeneratorPath = options.customGeneratorPath.orEmpty()
        generator.mode = options.generatorMode
    }

    override fun applyEditorTo(configuration: LezerGeneratorRunConfiguration) {
        val options = configuration.options
        options.grammarFile = grammarFile.text.trim().ifEmpty { null }
        options.outputFile = outputFile.text.trim().ifEmpty { null }
        options.workingDirectory = workingDirectory.text.trim().ifEmpty { null }
        options.cjs = cjs.isSelected
        options.generateOnSave = generateOnSave.isSelected
        options.typeScript = typeScript.isSelected
        options.includeNames = includeNames.isSelected
        options.noTerms = noTerms.isSelected
        options.exportName = exportName.text.trim().ifEmpty { null }
        options.generatorMode = generator.mode
        options.customGeneratorPath = generator.customGeneratorPath.trim().ifEmpty { null }
    }
}
