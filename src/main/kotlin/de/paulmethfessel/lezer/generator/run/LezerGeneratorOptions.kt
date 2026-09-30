package de.paulmethfessel.lezer.generator.run

import com.intellij.execution.configurations.LocatableRunConfigurationOptions
import com.intellij.util.xmlb.annotations.Transient
import de.paulmethfessel.lezer.generator.GeneratorMode

/** Options of a generator run, each one maps to a command line option of `lezer-generator`. */
class LezerGeneratorOptions : LocatableRunConfigurationOptions() {
    var grammarFile by string()

    /** `--output`, the parser is printed to the console if it is empty. */
    var outputFile by string()

    /** `--cjs`: CommonJS instead of ES modules. */
    var cjs by property(false)

    /** `--names`: include node names in the parser, for debugging. */
    var includeNames by property(false)

    /** `--noTerms`: don't write the `.terms.js` file. */
    var noTerms by property(false)

    /** `--typeScript`: generate TypeScript instead of JavaScript. */
    var typeScript by property(false)

    /** `--export`: the name of the exported parser, `parser` by default. */
    var exportName by string()

    /** The generator installation to use, `null` for the project's setting. */
    var generator by string()
    var customGeneratorPath by string()

    /** Relative paths are resolved against it, the grammar's directory by default. */
    var workingDirectory by string()

    /** Runs the configuration in the background whenever the grammar file is saved. */
    var generateOnSave by property(false)

    @get:Transient
    var generatorMode: GeneratorMode?
        get() = generator?.let { name -> GeneratorMode.entries.find { it.name == name } }
        set(value) {
            generator = value?.name
        }
}
