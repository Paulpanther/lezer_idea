package de.paulmethfessel.lezer.generator

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class GeneratorLocatorTest : BasePlatformTestCase() {
    fun testCustomMode() {
        val pkg = generatorPackage("""{"version": "1.8.1"}""")
        val generator = GeneratorLocator.resolve(project, null, GeneratorMode.CUSTOM, pkg.toString(), allowProcess = false)!!
        assertEquals(GeneratorKind.CUSTOM, generator.kind)
        assertEquals("1.8.1", generator.version)
        assertNull(GeneratorLocator.resolve(project, null, GeneratorMode.CUSTOM, "/does/not/exist", allowProcess = false))
        assertNull(GeneratorLocator.resolve(project, null, GeneratorMode.CUSTOM, " ", allowProcess = false))
    }

    fun testEntryPointsFromPackageJson() {
        val pkg = generatorPackage("""{"bin": "./cli.cjs", "exports": "./index.mjs"}""")
        val generator = GeneratorLocator.load(pkg, GeneratorKind.CUSTOM)!!
        assertEquals(pkg.resolve("cli.cjs"), generator.cli)
        assertEquals(pkg.resolve("index.mjs"), generator.module)
        assertNull(generator.version)

        // Without `exports`, `module` is the ES module
        val legacy = generatorPackage("""{"module": "esm.js", "main": "cjs.js"}""")
        assertEquals(legacy.resolve("esm.js"), GeneratorLocator.load(legacy, GeneratorKind.CUSTOM)!!.module)
    }

    fun testDefaultEntryPoints() {
        // Older or unusual package.json files, and invalid JSON, fall back to the layout of @lezer/generator 1.x
        for (json in listOf("{}", "not json")) {
            val pkg = generatorPackage(json)
            val generator = GeneratorLocator.load(pkg, GeneratorKind.LOCAL)!!
            assertEquals(pkg.resolve("src/lezer-generator.cjs"), generator.cli)
            assertEquals(pkg.resolve("dist/index.js"), generator.module)
        }
        assertNull(GeneratorLocator.load(Files.createTempDirectory("empty"), GeneratorKind.LOCAL))
    }

    fun testDefaultGlobalRoot() {
        assertEquals(Path("/usr/local/lib/node_modules"), GeneratorLocator.defaultGlobalRoot(NodeInstallation(Path("/usr/local/bin/node")), isWindows = false))
        assertEquals(Path("C:/nodejs/node_modules"), GeneratorLocator.defaultGlobalRoot(NodeInstallation(Path("C:/nodejs/node.exe")), isWindows = true))
    }

    private fun generatorPackage(json: String): Path {
        val pkg = Files.createTempDirectory("lezer").resolve("node_modules/@lezer/generator").createDirectories()
        pkg.resolve("package.json").writeText(json)
        return pkg
    }
}
