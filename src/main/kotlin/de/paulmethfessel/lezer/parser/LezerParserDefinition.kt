package de.paulmethfessel.lezer.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import de.paulmethfessel.lezer.LezerLanguage
import de.paulmethfessel.lezer.lexer.LezerLexerAdapter
import de.paulmethfessel.lezer.psi.LezerFile
import de.paulmethfessel.lezer.psi.LezerTokenSets
import de.paulmethfessel.lezer.psi.LezerTypes

class LezerParserDefinition : ParserDefinition {
    override fun createLexer(project: Project?): Lexer = LezerLexerAdapter()

    override fun createParser(project: Project?): PsiParser = LezerParser()

    override fun getFileNodeType(): IFileElementType = FILE

    override fun getCommentTokens(): TokenSet = LezerTokenSets.COMMENTS

    override fun getStringLiteralElements(): TokenSet = LezerTokenSets.STRINGS

    override fun createElement(node: ASTNode): PsiElement = LezerTypes.Factory.createElement(node)

    override fun createFile(viewProvider: FileViewProvider): PsiFile = LezerFile(viewProvider)

    companion object {
        @JvmField
        val FILE = IFileElementType(LezerLanguage)
    }
}
