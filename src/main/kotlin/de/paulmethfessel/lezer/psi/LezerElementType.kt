package de.paulmethfessel.lezer.psi

import com.intellij.psi.tree.IElementType
import de.paulmethfessel.lezer.LezerLanguage
import org.jetbrains.annotations.NonNls

class LezerElementType(@NonNls debugName: String) : IElementType(debugName, LezerLanguage)
