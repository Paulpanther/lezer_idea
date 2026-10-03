package de.paulmethfessel.lezer

import de.paulmethfessel.lezer.annotator.LezerErrorMessages
import junit.framework.TestCase

/** Grammar-Kit's parser error messages and their simplified versions, so changes of its format are noticed. */
class LezerErrorMessagesTest : TestCase() {
    fun testSimplify() {
        val cases = listOf(
            // A missing closing delimiter is the likely cause, the alternatives that continue the construct are noise
            "'!', '|', '}' or <prop> expected, got '@skip'" to "'}' expected, got '@skip'",
            "',', '*' or '>' expected, got 'x'" to "',' or '>' expected, got 'x'",
            // Rule names lose their angle brackets, unquoted angle brackets are tokens
            "<expression> expected" to "expression expected",
            "<, identifier or '{' expected" to "'<', identifier or '{' expected",
            // Quoted tokens may contain the separators
            "',', ' or ' or identifier expected" to "',', ' or ' or identifier expected",
            // Only continuations: they are kept
            "'*' or '+' expected" to "'*' or '+' expected",
            "'?' expected, got ';'" to "'?' expected, got ';'",
            // Other messages are unchanged
            "Unexpected token" to "Unexpected token",
        )
        for ((message, simplified) in cases) assertEquals(message, simplified, LezerErrorMessages.simplify(message))
    }
}
