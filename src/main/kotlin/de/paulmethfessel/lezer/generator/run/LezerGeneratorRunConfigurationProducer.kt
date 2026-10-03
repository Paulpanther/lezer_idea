package de.paulmethfessel.lezer.generator.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import de.paulmethfessel.lezer.psi.LezerFile

/** Creates a generator run configuration for the grammar file in the editor or project view. */
class LezerGeneratorRunConfigurationProducer : LazyRunConfigurationProducer<LezerGeneratorRunConfiguration>() {
    override fun getConfigurationFactory(): ConfigurationFactory = LezerGeneratorConfigurationType.getInstance()

    override fun setupConfigurationFromContext(
        configuration: LezerGeneratorRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>,
    ): Boolean {
        val file = grammarFile(context) ?: return false
        LezerGeneratorConfigurations.applyDefaults(configuration, file)
        return true
    }

    override fun isConfigurationFromContext(configuration: LezerGeneratorRunConfiguration, context: ConfigurationContext): Boolean =
        grammarFile(context)?.path == configuration.options.grammarFile

    private fun grammarFile(context: ConfigurationContext): VirtualFile? =
        (context.psiLocation?.containingFile as? LezerFile)?.virtualFile
}
