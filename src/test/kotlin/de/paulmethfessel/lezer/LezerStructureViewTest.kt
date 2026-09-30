package de.paulmethfessel.lezer

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class LezerStructureViewTest : BasePlatformTestCase() {
    fun testStructure() {
        myFixture.configureByText(
            "test.grammar",
            """
            @precedence { times @left, plus @left }
            @top Program { expr* }
            expr { Number | Binary { expr !times "*" expr } | [@name=Anon] { Nested { "x" } } }
            list<item> { item ("," item)* }
            @skip { space }
            @skip {} { String { '"' '"' } }
            @tokens {
              Number { @digit+ }
              space { " "+ }
            }
            @external tokens insertSemi from "./tokens" { semi }
            @external prop myProp from "./props"
            @dialects { ts, jsx }
            """.trimIndent(),
        )
        myFixture.testStructureView { component ->
            PlatformTestUtil.expandAll(component.tree)
            PlatformTestUtil.assertTreeEqual(
                component.tree,
                """
                -test.grammar
                 -@precedence
                  times
                  plus
                 Program
                 -expr
                  Binary
                  Nested
                 list<item>
                 -@skip
                  String
                 -@tokens
                  Number
                  space
                 -insertSemi
                  semi
                 myProp
                 -@dialects
                  ts
                  jsx

                """.trimIndent(),
            )
        }
    }
}
