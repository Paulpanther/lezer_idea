package de.paulmethfessel.lezer.generator.run

import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/** The project's generator run configurations of a grammar file. */
object LezerGeneratorConfigurations {
    fun forGrammar(project: Project, file: VirtualFile): List<RunnerAndConfigurationSettings> =
        RunManager.getInstance(project).getConfigurationSettingsList(LezerGeneratorConfigurationType.getInstance())
            .filter { (it.configuration as? LezerGeneratorRunConfiguration)?.grammarVirtualFile() == file }

    /** Adds a configuration with the same defaults as one created from the editor's context. */
    fun create(project: Project, file: VirtualFile): RunnerAndConfigurationSettings {
        val runManager = RunManager.getInstance(project)
        val settings = runManager.createConfiguration("Generate ${file.name}", LezerGeneratorConfigurationType.getInstance())
        applyDefaults(settings.configuration as LezerGeneratorRunConfiguration, file)
        runManager.addConfiguration(settings)
        return settings
    }

    fun applyDefaults(configuration: LezerGeneratorRunConfiguration, file: VirtualFile) {
        val options = configuration.options
        options.grammarFile = file.path
        options.outputFile = file.parent.path + "/parser"
        configuration.name = "Generate ${file.name}"
    }
}
