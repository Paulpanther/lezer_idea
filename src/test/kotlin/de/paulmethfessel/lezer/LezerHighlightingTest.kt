package de.paulmethfessel.lezer

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class LezerHighlightingTest : BasePlatformTestCase() {
    override fun getTestDataPath(): String = "src/test/testData/highlighting"

    fun testAnnotator() {
        myFixture.testHighlighting(false, true, false, "Annotator.grammar")
    }
}
