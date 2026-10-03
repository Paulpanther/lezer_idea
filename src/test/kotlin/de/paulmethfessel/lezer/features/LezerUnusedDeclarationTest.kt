package de.paulmethfessel.lezer.features

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.paulmethfessel.lezer.inspections.LezerUnusedDeclarationInspection

class LezerUnusedDeclarationTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(LezerUnusedDeclarationInspection())
    }

    fun testHighlighting() {
        myFixture.configureByText(
            "test.grammar",
            """
            @top Program { used* }
            used { "a" | Nested { "b" } | commaSep<ident> }
            <warning descr="Rule 'unused' is never used">unused</warning> { "u" }
            <warning descr="Rule 'recursive' is never used">recursive</warning> { "(" recursive ")" | "x" }
            <warning descr="Rule 'template' is never used">template</warning><x> { x }
            commaSep<item> { item ("," item)* }
            exported[@export] { "e" }
            @tokens {
              ident { @asciiLetter+ }
              <warning descr="Token 'unusedToken' is never used">unusedToken</warning> { "t" }
            }
            @external tokens ext from "./ext" { External }
            """.trimIndent(),
        )
        myFixture.checkHighlighting(true, false, false)
    }

    fun testRemoveWithDocComment() {
        myFixture.configureByText(
            "test.grammar",
            """
            @top Program { "a" }

            // Not needed anymore
            <caret>unused { "u" }

            other[@export] { "o" }
            """.trimIndent(),
        )
        // The preview must show the same result
        myFixture.checkPreviewAndLaunchAction(myFixture.findSingleIntention("Remove unused rule 'unused'"))
        myFixture.checkResult(
            """
            @top Program { "a" }

            other[@export] { "o" }
            """.trimIndent(),
        )
    }
}
