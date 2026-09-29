package de.paulmethfessel.lezer

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

object LezerFileType : LanguageFileType(LezerLanguage) {
    override fun getName(): String = "Lezer Grammar"

    override fun getDescription(): String = "Lezer grammar file"

    override fun getDefaultExtension(): String = "grammar"

    override fun getIcon(): Icon = LezerIcons.FILE
}
