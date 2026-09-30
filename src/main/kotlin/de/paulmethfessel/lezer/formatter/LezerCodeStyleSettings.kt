package de.paulmethfessel.lezer.formatter

import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CustomCodeStyleSettings

/** Lezer specific code style options, in addition to the common ones. */
@Suppress("PropertyName")
class LezerCodeStyleSettings(container: CodeStyleSettings) : CustomCodeStyleSettings("LezerCodeStyleSettings", container) {
    /** `[@name=A, @dialect=b]` instead of `[@name=A,@dialect=b]` (the official grammars use no space). */
    @JvmField
    var SPACE_AFTER_COMMA_IN_PROPS = false

    /** `{ a b }` instead of `{a b}` for rule bodies and other braced blocks on a single line. */
    @JvmField
    var SPACE_WITHIN_BRACES = true
}
