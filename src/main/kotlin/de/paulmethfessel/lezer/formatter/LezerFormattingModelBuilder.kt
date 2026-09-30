package de.paulmethfessel.lezer.formatter

import com.intellij.formatting.FormattingContext
import com.intellij.formatting.FormattingModel
import com.intellij.formatting.FormattingModelBuilder
import com.intellij.formatting.FormattingModelProvider
import com.intellij.formatting.Indent
import de.paulmethfessel.lezer.LezerLanguage

class LezerFormattingModelBuilder : FormattingModelBuilder {
    override fun createModel(formattingContext: FormattingContext): FormattingModel {
        val settings = formattingContext.codeStyleSettings
        val spacing = LezerSpacing(
            settings.getCommonSettings(LezerLanguage),
            settings.getCustomSettings(LezerCodeStyleSettings::class.java),
        )
        val root = LezerBlock(formattingContext.node, Indent.getNoneIndent(), spacing)
        return FormattingModelProvider.createFormattingModelForPsiFile(formattingContext.containingFile, root, settings)
    }
}
