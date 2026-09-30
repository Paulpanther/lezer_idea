package de.paulmethfessel.lezer.generator.run

import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.SimpleConfigurationType
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NotNullLazyValue
import de.paulmethfessel.lezer.LezerIcons

class LezerGeneratorConfigurationType : SimpleConfigurationType(
    "LezerGenerator",
    "Lezer Generator",
    "Generates a parser from a Lezer grammar with @lezer/generator",
    NotNullLazyValue.createValue { LezerIcons.FILE },
) {
    override fun createTemplateConfiguration(project: Project): RunConfiguration =
        LezerGeneratorRunConfiguration(project, this, "Lezer Generator")

    override fun getOptionsClass(): Class<out BaseState> = LezerGeneratorOptions::class.java

    companion object {
        fun getInstance(): LezerGeneratorConfigurationType =
            ConfigurationTypeUtil.findConfigurationType(LezerGeneratorConfigurationType::class.java)
    }
}
