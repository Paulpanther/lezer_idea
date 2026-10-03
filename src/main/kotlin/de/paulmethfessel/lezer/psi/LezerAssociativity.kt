package de.paulmethfessel.lezer.psi

/** The associativity of a precedence in `@precedence { name @left }`. */
enum class LezerAssociativity(val keyword: String?, val description: String) {
    LEFT("left", "left associative"),
    RIGHT("right", "right associative"),
    CUT("cut", "cut"),
    NONE(null, "no associativity"),
}
