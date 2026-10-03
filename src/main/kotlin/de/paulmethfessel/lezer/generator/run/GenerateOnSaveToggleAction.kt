package de.paulmethfessel.lezer.generator.run

import com.intellij.execution.RunManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ex.CheckboxAction
import com.intellij.openapi.vfs.VirtualFile
import de.paulmethfessel.lezer.LezerFileType

/**
 * Toggles "generate on save" for the grammar in the editor, on all of its generator configurations. Enabling it creates
 * a configuration if there is none yet.
 */
class GenerateOnSaveToggleAction : CheckboxAction(
    "Regenerate on save",
    "Regenerate the parser in the background when the grammar is saved",
    null,
) {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isEnabledAndVisible = e.project != null && grammar(e) != null
    }

    override fun isSelected(e: AnActionEvent): Boolean {
        val project = e.project ?: return false
        val file = grammar(e) ?: return false
        val configurations = LezerGeneratorConfigurations.forGrammar(project, file)
        return configurations.isNotEmpty() &&
            configurations.all { (it.configuration as LezerGeneratorRunConfiguration).options.generateOnSave }
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val project = e.project ?: return
        val file = grammar(e) ?: return
        val runManager = RunManager.getInstance(project)
        val settings = LezerGeneratorConfigurations.forGrammar(project, file)
            .ifEmpty { if (state) listOf(LezerGeneratorConfigurations.create(project, file)) else return }
        for (setting in settings) {
            val configuration = setting.configuration as LezerGeneratorRunConfiguration
            configuration.options.generateOnSave = state
            if (!state) continue
            // A temporary configuration would be dropped once others replace it.
            if (setting.isTemporary) runManager.makeStable(setting)
            GenerateOnSave.run(project, configuration)
        }
    }

    private fun grammar(e: AnActionEvent): VirtualFile? =
        e.getData(CommonDataKeys.VIRTUAL_FILE)?.takeIf { it.fileType == LezerFileType }
}
