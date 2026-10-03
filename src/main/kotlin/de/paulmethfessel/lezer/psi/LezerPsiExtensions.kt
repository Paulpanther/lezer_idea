package de.paulmethfessel.lezer.psi

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType

/** The name of a pseudo-prop like `@name` or `@export`, null for props like `closedBy`. */
val LezerProp.pseudoName: String?
    get() = firstChild.takeIf { it.elementType == LezerTypes.AT_NAME }?.text

/** The pseudo-prop [name] (like `@name`) of the declaration, if it has one. */
fun LezerNamedElement.pseudoProp(name: String): LezerProp? =
    PsiTreeUtil.getChildOfType(this, LezerProps::class.java)?.propList?.firstOrNull { it.pseudoName == name }

val LezerPrecedence.associativity: LezerAssociativity
    get() = when (lastChild.elementType) {
        LezerTypes.AT_LEFT -> LezerAssociativity.LEFT
        LezerTypes.AT_RIGHT -> LezerAssociativity.RIGHT
        LezerTypes.AT_CUT -> LezerAssociativity.CUT
        else -> LezerAssociativity.NONE
    }
