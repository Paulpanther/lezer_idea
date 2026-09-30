package de.paulmethfessel.lezer.generator

import de.paulmethfessel.lezer.generator.run.GeneratorArguments
import de.paulmethfessel.lezer.generator.run.LezerGeneratorOptions
import junit.framework.TestCase

class GeneratorArgumentsTest : TestCase() {
    fun testDefaults() {
        val options = LezerGeneratorOptions().apply { grammarFile = "/src/lang.grammar" }
        assertEquals(listOf("/src/lang.grammar"), GeneratorArguments.of(options))
        assertEquals(emptyList<String>(), GeneratorArguments.outputFiles(options))
    }

    fun testAllOptions() {
        val options = LezerGeneratorOptions().apply {
            grammarFile = "lang.grammar"
            outputFile = "src/parser"
            cjs = true
            includeNames = true
            noTerms = true
            typeScript = true
            exportName = "langParser"
        }
        assertEquals(
            listOf("--cjs", "--names", "--noTerms", "--typeScript", "--export", "langParser", "--output", "src/parser", "lang.grammar"),
            GeneratorArguments.of(options),
        )
        assertEquals(listOf("src/parser.ts"), GeneratorArguments.outputFiles(options))
    }

    fun testOutputFiles() {
        val options = LezerGeneratorOptions().apply { outputFile = "parser.mjs" }
        assertEquals(listOf("parser.mjs", "parser.terms.mjs"), GeneratorArguments.outputFiles(options))
        options.outputFile = "parser"
        assertEquals(listOf("parser.js", "parser.terms.js"), GeneratorArguments.outputFiles(options))
    }
}
