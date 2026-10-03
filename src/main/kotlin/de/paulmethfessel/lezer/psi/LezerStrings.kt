package de.paulmethfessel.lezer.psi

object LezerStrings {
    private val ESCAPE = Regex("""\\(.)""")

    /** The content of a quoted string like `"a\"b"`, with escapes resolved; other text is returned unchanged. */
    fun unquote(text: String): String {
        if (text.length < 2 || text.first() !in "\"'" || text.last() != text.first()) return text
        return ESCAPE.replace(text.substring(1, text.length - 1)) { it.groupValues[1] }
    }
}
