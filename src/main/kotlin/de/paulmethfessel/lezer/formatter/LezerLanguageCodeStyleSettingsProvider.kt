package de.paulmethfessel.lezer.formatter

import com.intellij.application.options.CodeStyleAbstractConfigurable
import com.intellij.application.options.CodeStyleAbstractPanel
import com.intellij.application.options.IndentOptionsEditor
import com.intellij.application.options.TabbedLanguageCodeStylePanel
import com.intellij.lang.Language
import com.intellij.psi.codeStyle.CodeStyleConfigurable
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizableOptions
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.codeStyle.CustomCodeStyleSettings
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider
import de.paulmethfessel.lezer.LezerLanguage

class LezerLanguageCodeStyleSettingsProvider : LanguageCodeStyleSettingsProvider() {
    override fun getLanguage(): Language = LezerLanguage

    override fun createCustomSettings(settings: CodeStyleSettings): CustomCodeStyleSettings =
        LezerCodeStyleSettings(settings)

    override fun createConfigurable(
        baseSettings: CodeStyleSettings,
        modelSettings: CodeStyleSettings,
    ): CodeStyleConfigurable = object : CodeStyleAbstractConfigurable(baseSettings, modelSettings, configurableDisplayName) {
        override fun createPanel(settings: CodeStyleSettings): CodeStyleAbstractPanel =
            object : TabbedLanguageCodeStylePanel(LezerLanguage, currentSettings, settings) {}
    }

    override fun getIndentOptionsEditor(): IndentOptionsEditor = IndentOptionsEditor(this)

    override fun customizeDefaults(commonSettings: CommonCodeStyleSettings, indentOptions: CommonCodeStyleSettings.IndentOptions) {
        indentOptions.INDENT_SIZE = 2
        indentOptions.CONTINUATION_INDENT_SIZE = 2
        indentOptions.TAB_SIZE = 2
        commonSettings.KEEP_BLANK_LINES_IN_CODE = 1
        commonSettings.KEEP_BLANK_LINES_BEFORE_RBRACE = 0
    }

    override fun customizeSettings(consumer: CodeStyleSettingsCustomizable, settingsType: SettingsType) {
        val options = CodeStyleSettingsCustomizableOptions.getInstance()
        when (settingsType) {
            SettingsType.SPACING_SETTINGS -> {
                consumer.showStandardOptions("SPACE_WITHIN_PARENTHESES", "SPACE_BEFORE_COMMA", "SPACE_AFTER_COMMA")
                consumer.showCustomOption(
                    LezerCodeStyleSettings::class.java, "SPACE_WITHIN_BRACES", "Braces", options.SPACES_WITHIN,
                )
                consumer.showCustomOption(
                    LezerCodeStyleSettings::class.java, "SPACE_AFTER_COMMA_IN_PROPS", "After comma in props",
                    options.SPACES_OTHER,
                )
            }
            SettingsType.BLANK_LINES_SETTINGS ->
                consumer.showStandardOptions("KEEP_BLANK_LINES_IN_CODE", "KEEP_BLANK_LINES_BEFORE_RBRACE")
            SettingsType.WRAPPING_AND_BRACES_SETTINGS -> consumer.showStandardOptions("KEEP_LINE_BREAKS")
            else -> {}
        }
    }

    override fun getCodeSample(settingsType: SettingsType): String = CODE_SAMPLE

    private companion object {
        val CODE_SAMPLE = """
            @precedence { times @left, plus @left }

            @top Program { expression* }


            expression[@isGroup=Expression,@dialect=ts] {
              Number |
              BinaryExpression {
                expression !times "*" expression |
                expression !plus ("+" | "-") expression
              } |
              ParenExpression { "(" commaSep<expression> ")" }
            }

            commaSep<item> { item ("," item)* }

            @skip { space | LineComment }

            @tokens {
              Number { @digit+ }
              space { $[ \t\n\r]+ }
              LineComment { "//" ![\n]* }
              "(" ")"
            }
        """.trimIndent()
    }
}
