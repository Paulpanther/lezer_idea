package de.paulmethfessel.lezer

import com.intellij.openapi.project.Project
import de.paulmethfessel.lezer.generator.NodeInstallation
import de.paulmethfessel.lezer.generator.NodeLocator
import junit.framework.TestCase.fail

/** Node.js and the `@lezer/generator` package for tests that run the real generator. */
class GeneratorSetup(val node: NodeInstallation, val generatorDir: String)

/**
 * The generator setup from `LEZER_GENERATOR_DIR` (see the README), or null if it is missing. Missing setup fails on
 * CI, where it is always installed, so that these tests can't pass without running.
 */
fun generatorSetup(project: Project, test: String): GeneratorSetup? {
    val generatorDir = System.getenv("LEZER_GENERATOR_DIR")
    val node = NodeLocator.find(project)
    if (generatorDir != null && node != null) return GeneratorSetup(node, generatorDir)
    val reason = if (generatorDir == null) "LEZER_GENERATOR_DIR is not set" else "Node.js was not found"
    if (System.getenv("CI") != null) fail("$test needs the generator on CI: $reason")
    println("Skipping $test: $reason")
    return null
}
