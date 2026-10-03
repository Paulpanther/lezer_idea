package de.paulmethfessel.lezer

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataMap
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.paulmethfessel.lezer.psi.LezerDeclarationKind
import de.paulmethfessel.lezer.psi.LezerInlineRuleExpression
import de.paulmethfessel.lezer.psi.LezerRuleDeclaration
import de.paulmethfessel.lezer.structure.LezerNavBarModelExtension

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

    fun testNavBar() {
        val file = myFixture.configureByText(
            "test.grammar",
            "@top P { list<A> }\n@tokens { Num<caret>ber { @digit+ } }\nlist<item> { item+ Inner { \"i\" } }\nA { \"a\" }",
        )
        val navBar = LezerNavBarModelExtension()
        val dataMap = object : DataMap {
            @Suppress("UNCHECKED_CAST")
            override fun <T : Any> get(key: DataKey<T>): T? = when (key) {
                CommonDataKeys.PSI_FILE -> file as T
                CommonDataKeys.EDITOR -> myFixture.editor as T
                else -> null
            }
        }

        // The caret is in the token, which is in the @tokens block, which is in the file
        val token = navBar.getLeafElement(dataMap)!!
        assertEquals("Number", navBar.getPresentableText(token))
        assertEquals(LezerDeclarationKind.TOKEN.icon, navBar.getIcon(token))
        val tokens = navBar.getParent(token)!!
        assertEquals("@tokens", navBar.getPresentableText(tokens))
        assertSame(file, navBar.getParent(tokens))
        assertNull(navBar.getParent(file))

        assertEquals(listOf("P", "@tokens", "list<item>", "A"), children(navBar, file))
        assertEquals(listOf("Number"), children(navBar, tokens))
        val template = PsiTreeUtil.getChildrenOfType(file, LezerRuleDeclaration::class.java)!!.first()
        assertEquals(listOf("Inner"), children(navBar, template))
        assertSame(template, navBar.getParent(PsiTreeUtil.findChildOfType(template, LezerInlineRuleExpression::class.java)))
    }

    private fun children(navBar: LezerNavBarModelExtension, element: PsiElement): List<String?> {
        val children = mutableListOf<Any>()
        navBar.processChildren(element, null) { children += it; true }
        return children.map { navBar.getPresentableText(it) }
    }
}
