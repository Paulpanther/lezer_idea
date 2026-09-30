package de.paulmethfessel.lezer.features

import com.intellij.testFramework.utils.inlays.declarative.DeclarativeInlayHintsProviderTestCase
import de.paulmethfessel.lezer.hints.LezerPrecedenceInlayHintsProvider

class LezerPrecedenceInlayHintsTest : DeclarativeInlayHintsProviderTestCase() {
    fun testPrecedenceMarkers() {
        doTestProvider(
            "test.grammar",
            """
            @precedence { times @left, plus @right, call }
            @top P { e }
            e { e !times/*<# #1 left #>*/ "*" e | e !plus/*<# #2 right #>*/ "+" e | e !call/*<# #3 #>*/ "()" | e !unknown "x" | "1" }
            """.trimIndent(),
            LezerPrecedenceInlayHintsProvider(),
        )
    }
}
