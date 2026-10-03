package de.paulmethfessel.lezer.playground

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import de.paulmethfessel.lezer.psi.*

/** What the playground needs to know about a grammar besides its text. */
class PlaygroundGrammarInfo(
    val externals: List<ExternalDeclaration>,
    val tops: List<String>,
    val dialects: List<String>,
) {
    companion object {
        fun of(file: LezerFile): PlaygroundGrammarInfo {
            val externals = PsiTreeUtil.findChildrenOfAnyType(
                file,
                LezerExternalTokensDeclaration::class.java,
                LezerExternalPropDeclaration::class.java,
                LezerExternalPropSourceDeclaration::class.java,
                LezerExternalSpecializeDeclaration::class.java,
                LezerContextDeclaration::class.java,
            ).mapNotNull(::external)
            val tops = PsiTreeUtil.findChildrenOfType(file, LezerTopRuleDeclaration::class.java).mapNotNull { it.name }
            val dialects = PsiTreeUtil.findChildrenOfType(file, LezerDialect::class.java).mapNotNull { it.name }
            return PlaygroundGrammarInfo(externals, tops, dialects)
        }

        /** The kind, the name the generator asks the implementation for and the module to import it from. */
        private fun external(declaration: PsiElement): ExternalDeclaration? {
            val kind = when (declaration) {
                is LezerExternalTokensDeclaration -> "tokenizer"
                is LezerExternalPropDeclaration -> "prop"
                is LezerExternalPropSourceDeclaration -> "propSource"
                is LezerExternalSpecializeDeclaration -> "specializer"
                is LezerContextDeclaration -> "context"
                else -> return null
            }
            // For `@external prop name as alias`, the module exports `name`
            val name = PsiTreeUtil.getChildOfType(declaration, LezerSimpleName::class.java)?.text ?: return null
            val source = declaration.node.findChildByType(LezerTypes.STRING)?.text ?: return null
            return ExternalDeclaration(kind, name, source.substring(1, source.length - 1))
        }
    }
}
