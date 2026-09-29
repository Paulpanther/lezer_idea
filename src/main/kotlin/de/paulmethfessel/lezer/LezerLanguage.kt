package de.paulmethfessel.lezer

import com.intellij.lang.Language

object LezerLanguage : Language("Lezer") {
    @Suppress("unused")
    private fun readResolve(): Any = LezerLanguage

    override fun getDisplayName(): String = "Lezer Grammar"
}
