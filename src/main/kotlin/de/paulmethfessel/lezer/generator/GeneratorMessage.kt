package de.paulmethfessel.lezer.generator

import com.intellij.openapi.editor.Document

/**
 * A message of the generator. If it refers to a position, the generator appends it as ` (fileName line:column)`, with a
 * one-based line and zero-based column.
 */
data class GeneratorMessage(val text: String, val line: Int?, val column: Int?) {
    /** The first line, conflict messages continue with the conflicting rules. */
    val summary: String get() = text.lineSequence().first()

    val isUnusedRule: Boolean get() = text.startsWith("Unused rule")

    fun offset(document: Document): Int? {
        if (line == null || column == null || line < 1 || line > document.lineCount) return null
        val start = document.getLineStartOffset(line - 1)
        return (start + column).coerceAtMost(document.getLineEndOffset(line - 1))
    }

    companion object {
        private val POSITION = Regex("""^(.*) \(${GeneratorCheck.FILE_NAME} (\d+):(\d+)\)$""", RegexOption.DOT_MATCHES_ALL)

        fun parse(message: String): GeneratorMessage {
            val match = POSITION.matchEntire(message.trimEnd()) ?: return GeneratorMessage(message.trimEnd(), null, null)
            val (text, line, column) = match.destructured
            return GeneratorMessage(text, line.toInt(), column.toInt())
        }
    }
}
