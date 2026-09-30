package de.paulmethfessel.lezer.generator.run

/** Command line arguments of `lezer-generator`, see its `src/lezer-generator.cjs`. */
object GeneratorArguments {
    fun of(options: LezerGeneratorOptions): List<String> = buildList {
        if (options.cjs) add("--cjs")
        if (options.includeNames) add("--names")
        if (options.noTerms) add("--noTerms")
        if (options.typeScript) add("--typeScript")
        options.exportName?.takeIf { it.isNotBlank() }?.let { add("--export"); add(it.trim()) }
        options.outputFile?.takeIf { it.isNotBlank() }?.let { add("--output"); add(it.trim()) }
        add(options.grammarFile.orEmpty())
    }

    /** The files the generator writes, see `lezer-generator.cjs`. */
    fun outputFiles(options: LezerGeneratorOptions): List<String> {
        val out = options.outputFile?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()
        val extension = Regex("""^(.*)\.(c?js|mjs|ts|esm?)$""").matchEntire(out)
        val (parser, terms) = if (extension != null) {
            out to "${extension.groupValues[1]}.terms.${extension.groupValues[2]}"
        } else {
            val ext = if (options.typeScript) "ts" else "js"
            "$out.$ext" to "$out.terms.$ext"
        }
        return if (options.noTerms) listOf(parser) else listOf(parser, terms)
    }
}
