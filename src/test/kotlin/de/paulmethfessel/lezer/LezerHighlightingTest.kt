package de.paulmethfessel.lezer

import com.intellij.lang.LanguageAnnotators
import com.intellij.openapi.project.DumbService
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class LezerHighlightingTest : BasePlatformTestCase() {
    override fun getTestDataPath(): String = "src/test/testData/highlighting"

    fun testAnnotator() {
        myFixture.testHighlighting(false, true, false, "Annotator.grammar")
    }

    /** The annotators only look at the file itself, so they also run while indexing. */
    fun testAnnotatorsRunInDumbMode() {
        val annotators = LanguageAnnotators.INSTANCE.allForLanguage(LezerLanguage)
            .filter { it.javaClass.packageName.startsWith("de.paulmethfessel.lezer") }
        assertEquals(2, annotators.size)
        assertEquals(annotators, DumbService.getInstance(project).filterByDumbAwareness(annotators))
    }
}
