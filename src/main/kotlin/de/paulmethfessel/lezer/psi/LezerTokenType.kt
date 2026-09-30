package de.paulmethfessel.lezer.psi

import com.intellij.psi.tree.IElementType
import de.paulmethfessel.lezer.LezerLanguage
import org.jetbrains.annotations.NonNls

class LezerTokenType(@NonNls debugName: String) : IElementType(debugName, LezerLanguage) {
    /** Readable name, used in parser error messages like `'}' expected, got '@skip'`. */
    override fun toString(): String {
        val name = super.toString()
        return READABLE_NAMES[name] ?: name
    }

    private companion object {
        val READABLE_NAMES = mapOf(
            "NAME" to "identifier",
            "STRING" to "string",
            "CHAR_SET" to "character set",
            "INVERTED_CHAR_SET" to "inverted character set",
            "CHAR_CLASS" to "character class",
            "AT_NAME" to "pseudo prop name",
            "LINE_COMMENT" to "line comment",
            "BLOCK_COMMENT" to "block comment",
        )
    }
}
