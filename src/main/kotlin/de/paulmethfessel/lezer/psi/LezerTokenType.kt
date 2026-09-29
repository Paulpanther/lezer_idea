package de.paulmethfessel.lezer.psi

import com.intellij.psi.tree.IElementType
import de.paulmethfessel.lezer.LezerLanguage
import org.jetbrains.annotations.NonNls

class LezerTokenType(@NonNls debugName: String) : IElementType(debugName, LezerLanguage) {
    override fun toString(): String = "LezerTokenType." + super.toString()
}
