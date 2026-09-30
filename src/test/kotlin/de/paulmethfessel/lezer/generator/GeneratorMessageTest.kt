package de.paulmethfessel.lezer.generator

import com.intellij.openapi.editor.impl.DocumentImpl
import junit.framework.TestCase

class GeneratorMessageTest : TestCase() {
    fun testPosition() {
        val message = GeneratorMessage.parse("Unused rule 'unused' (input 3:2)")
        assertEquals(GeneratorMessage("Unused rule 'unused'", 3, 2), message)
        assertTrue(message.isUnusedRule)
        assertEquals(14, message.offset(DocumentImpl("a\nbcdefghij\n  unused")))
    }

    fun testMultilineWithPosition() {
        val message = GeneratorMessage.parse("Rule x is generating a lot (300) of choices.\n  Consider splitting it up. (input 1:0)")
        assertEquals(1, message.line)
        assertEquals("Rule x is generating a lot (300) of choices.", message.summary)
    }

    fun testWithoutPosition() {
        val text = "shift/reduce conflict between\n  e -> e · \"+\" e\nand\n  e -> e \"+\" e"
        val message = GeneratorMessage.parse(text)
        assertEquals(GeneratorMessage(text, null, null), message)
        assertEquals("shift/reduce conflict between", message.summary)
        assertNull(message.offset(DocumentImpl("x")))
    }

    fun testPositionOutsideDocument() {
        assertNull(GeneratorMessage.parse("x (input 5:0)").offset(DocumentImpl("one line")))
        assertEquals(8, GeneratorMessage.parse("x (input 1:99)").offset(DocumentImpl("one line")))
    }
}
