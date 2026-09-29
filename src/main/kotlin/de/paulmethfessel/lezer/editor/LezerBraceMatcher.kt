package de.paulmethfessel.lezer.editor

import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import de.paulmethfessel.lezer.psi.LezerTypes.*

class LezerBraceMatcher : PairedBraceMatcher {
    override fun getPairs(): Array<BracePair> = PAIRS

    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?): Boolean = true

    override fun getCodeConstructStart(file: PsiFile, openingBraceOffset: Int): Int = openingBraceOffset

    companion object {
        private val PAIRS = arrayOf(
            BracePair(LBRACE, RBRACE, true),
            BracePair(LPAREN, RPAREN, false),
            BracePair(LBRACKET, RBRACKET, false),
            BracePair(LANGLE, RANGLE, false),
        )
    }
}
