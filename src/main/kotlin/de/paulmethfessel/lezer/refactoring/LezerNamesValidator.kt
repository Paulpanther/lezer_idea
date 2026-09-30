package de.paulmethfessel.lezer.refactoring

import com.intellij.lang.refactoring.NamesValidator
import com.intellij.openapi.project.Project

class LezerNamesValidator : NamesValidator {
    /** Contextual keywords like `from` are valid names, so there are no keywords. */
    override fun isKeyword(name: String, project: Project?): Boolean = false

    override fun isIdentifier(name: String, project: Project?): Boolean = name != "_" && IDENTIFIER.matches(name)

    private companion object {
        /** Same as `NAME` in Lezer.flex. */
        val IDENTIFIER = Regex("""[\p{IsAlphabetic}0-9_\-]+""")
    }
}
