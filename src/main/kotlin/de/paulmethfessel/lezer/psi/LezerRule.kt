package de.paulmethfessel.lezer.psi

/** A rule or top rule declaration, `name[props]<params> { body }`. */
interface LezerRule : LezerNamedElement {
    val paramList: LezerParamList?
    val body: LezerBody?
}
