package de.paulmethfessel.lezer.psi

import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiNameIdentifierOwner

/** A declaration in a Lezer grammar that has a name, e.g. a rule, token, parameter or precedence. */
interface LezerNamedElement : PsiNameIdentifierOwner, NavigatablePsiElement {
    val kind: LezerDeclarationKind
}
