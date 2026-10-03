package de.paulmethfessel.lezer.generator

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.ExternalLanguageAnnotators
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.paulmethfessel.lezer.LezerLanguage

/** How the generator's messages are shown, with a fixed report instead of running the generator. */
class LezerGeneratorAnnotatorTest : BasePlatformTestCase() {
    private var report = GeneratorReport()

    /** Reports [report] with the real annotator's [ExternalAnnotator.apply]. */
    private inner class FixedReportAnnotator : ExternalAnnotator<PsiFile, GeneratorReport>() {
        override fun collectInformation(file: PsiFile): PsiFile = file
        override fun doAnnotate(collectedInfo: PsiFile): GeneratorReport = report
        override fun apply(file: PsiFile, annotationResult: GeneratorReport, holder: AnnotationHolder) =
            LezerGeneratorAnnotator().apply(file, annotationResult, holder)
    }

    override fun setUp() {
        super.setUp()
        // The real annotator would run the generator
        LezerSettings.getInstance(project).state.liveErrors = false
        ExternalLanguageAnnotators.INSTANCE.addExplicitExtension(LezerLanguage, FixedReportAnnotator(), testRootDisposable)
    }

    override fun tearDown() {
        try {
            LezerSettings.getInstance(project).state.liveErrors = true
        } finally {
            super.tearDown()
        }
    }

    fun testPositionedMessages() {
        report = GeneratorReport(
            errors = listOf("Reference to undefined rule 'b' (input 1:11)"),
            warnings = listOf("Unused rule 'unused' (input 3:0)", "Overlapping tokens (input 2:6)"),
        )
        assertEquals(
            listOf("ERROR b: Reference to undefined rule 'b'", "WARNING \"x\": Overlapping tokens"),
            highlight("@top P { a b }\na { \"x\" }\nunused { \"y\" }\n"),
        )
    }

    fun testConflictWithoutPosition() {
        report = GeneratorReport(errors = listOf("shift/reduce conflict between\n  e -> e · \"+\" e\nand\n  e -> e \"+\" e"))
        assertEquals(
            listOf("ERROR \"+\": Shift/reduce conflict between 'e -> e · \"+\" e' and 'e -> e \"+\" e'"),
            highlight("@top P { e }\ne { e \"+\" e | \"x\" }\n"),
        )
    }

    fun testMessageWithoutPositionIsFileLevel() {
        report = GeneratorReport(errors = listOf("No @top rule"), failure = "TypeError: boom\n    at build")
        assertEquals(emptyList<String>(), highlight("a { \"x\" }\n"))
        val fileLevel = myFixture.doHighlighting().filter { it.isFileLevelAnnotation }.map { it.description }
        assertEquals(listOf("No @top rule", "lezer-generator failed: TypeError: boom"), fileLevel)
    }

    /** The annotations with a range in the file, file-level ones have none. */
    private fun highlight(text: String): List<String> {
        myFixture.configureByText("test.grammar", text)
        return myFixture.doHighlighting()
            .filter { !it.isFileLevelAnnotation && it.severity >= HighlightSeverity.WARNING }
            .map(::describe)
    }

    private fun describe(info: HighlightInfo) = "${info.severity.name} ${info.text}: ${info.description}"
}
