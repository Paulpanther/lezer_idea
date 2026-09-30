package de.paulmethfessel.lezer.generator

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.EditorNotificationPanel
import de.paulmethfessel.lezer.generator.run.LezerGeneratorConfigurationType
import de.paulmethfessel.lezer.generator.run.LezerGeneratorRunConfiguration
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class LezerGeneratorIntegrationTest : BasePlatformTestCase() {
    private val settings get() = LezerSettings.getInstance(project).state

    override fun tearDown() {
        try {
            settings.nodePath = null
            settings.generator = GeneratorMode.AUTO
            settings.customGeneratorPath = null
            settings.liveErrors = true
        } finally {
            super.tearDown()
        }
    }

    fun testGutterIconOnFirstTopOnly() {
        myFixture.configureByText("test.grammar", "@<caret>top A { b }\n@top B { b }\nb { \"b\" }")
        val gutters = myFixture.findGuttersAtCaret()
        assertEquals(listOf("Generate parser"), gutters.map { it.tooltipText })
        assertEquals(1, myFixture.findAllGutters().size)
    }

    fun testConfigurationFromContext() {
        val file = myFixture.configureByText("lang.grammar", "@top<caret> A { \"a\" }")
        val context = ConfigurationContext(file.findElementAt(myFixture.caretOffset)!!)
        val configuration = context.configuration!!.configuration as LezerGeneratorRunConfiguration
        assertEquals("Generate lang.grammar", configuration.name)
        assertEquals(file.virtualFile.path, configuration.options.grammarFile)
        assertEquals(file.virtualFile.parent.path + "/parser", configuration.options.outputFile)
    }

    fun testFindLocal() {
        val dir = Files.createTempDirectory("lezer")
        val pkg = dir.resolve("node_modules/@lezer/generator").createDirectories()
        pkg.resolve("package.json").writeText("""{"name": "@lezer/generator", "version": "1.2.3"}""")
        val grammar = dir.resolve("src").createDirectories().resolve("lang.grammar").apply { writeText("") }
        val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(grammar)!!

        val generator = GeneratorLocator.findLocal(project, file)!!
        assertEquals(GeneratorKind.LOCAL, generator.kind)
        assertEquals("1.2.3", generator.version)
        assertEquals(pkg, generator.packageDir)

        // Installs next to the closest package.json
        dir.resolve("package.json").writeText("{}")
        assertEquals(dir, GeneratorLocator.localInstallDirectory(project, file))
    }

    fun testBannerWithoutNode() {
        settings.nodePath = "/does/not/exist/node"
        assertEquals("Node.js was not found. It is needed to check grammars and generate parsers with lezer-generator.", bannerText())
    }

    fun testBannerWithoutGenerator() {
        settings.nodePath = Files.createTempFile("node", "").toString()
        settings.generator = GeneratorMode.CUSTOM
        settings.customGeneratorPath = "/does/not/exist"
        assertEquals(
            "@lezer/generator is not installed. Grammars are only checked for syntax errors and parsers cannot be generated.",
            bannerText(),
        )
    }

    fun testNoBannerWhenDismissed() {
        settings.nodePath = "/does/not/exist/node"
        settings.bannerDismissed = true
        try {
            assertNull(bannerText())
        } finally {
            settings.bannerDismissed = false
        }
    }

    /** Needs node and a generator package, e.g. `LEZER_GENERATOR_DIR=node_modules/@lezer/generator ./gradlew test`. */
    fun testLiveErrors() {
        val generatorDir = System.getenv("LEZER_GENERATOR_DIR") ?: return
        if (NodeLocator.find(project) == null) return
        settings.generator = GeneratorMode.CUSTOM
        settings.customGeneratorPath = generatorDir

        // The generator stops at the first error, so warnings are only reported for grammars without errors
        assertContainsElements(highlight("@top P { a }\na { \"x\" }\nunused { \"y\" }\n"), "WARNING unused: Unused rule 'unused'")
        assertContainsElements(highlight("@top P { a b }\na { \"x\" }\n"), "ERROR b: Reference to undefined rule 'b'")

        // Conflicts have no position, the conflicting symbol is found in the grammar instead
        assertContainsElements(
            highlight("@top P { e }\ne { e \"+\" e | \"x\" }\n"),
            "ERROR \"+\": Shift/reduce conflict between 'e -> e · \"+\" e' and 'e -> e \"+\" e'",
        )

        settings.liveErrors = false
        assertEmpty(highlight("@top P { a b }\na { \"x\" }\n").filter { it.startsWith("ERROR") })
    }

    /** Needs node and a generator package, see [testLiveErrors]. */
    fun testGenerateParser() {
        val generatorDir = System.getenv("LEZER_GENERATOR_DIR") ?: return
        if (NodeLocator.find(project) == null) return
        val dir = Files.createTempDirectory("lezer")
        dir.resolve("lang.grammar").writeText("@top P { \"x\" }")

        val configuration = LezerGeneratorConfigurationType.getInstance().createTemplateConfiguration(project) as LezerGeneratorRunConfiguration
        configuration.options.apply {
            grammarFile = dir.resolve("lang.grammar").toString()
            outputFile = "out/parser"
            typeScript = true
            generatorMode = GeneratorMode.CUSTOM
            customGeneratorPath = generatorDir
        }
        configuration.checkConfiguration()
        dir.resolve("out").createDirectories()
        val output = CapturingProcessHandler(configuration.createCommandLine()).runProcess(30_000)
        assertEquals(output.stderr, 0, output.exitCode)
        assertTrue(Files.isRegularFile(dir.resolve("out/parser.ts")))
        assertTrue(Files.isRegularFile(dir.resolve("out/parser.terms.ts")))
    }

    private fun highlight(text: String): List<String> {
        myFixture.configureByText("test.grammar", text)
        return myFixture.doHighlighting().map(::describe)
    }

    private fun describe(info: HighlightInfo) = "${info.severity.name} ${info.text}: ${info.description}"

    private fun bannerText(): String? {
        myFixture.configureByText("test.grammar", "@top A { \"a\" }")
        val file = myFixture.file.virtualFile
        val editor = FileEditorManager.getInstance(project).getSelectedEditor(file)!!
        val panel = LezerGeneratorNotificationProvider().collectNotificationData(project, file)?.apply(editor)
        return (panel as EditorNotificationPanel?)?.text
    }
}
