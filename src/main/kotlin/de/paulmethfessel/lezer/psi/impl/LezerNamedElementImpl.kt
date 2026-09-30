package de.paulmethfessel.lezer.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import de.paulmethfessel.lezer.psi.*
import javax.swing.Icon

abstract class LezerNamedElementImpl(node: ASTNode) : ASTWrapperPsiElement(node), LezerNamedElement {
    override val kind: LezerDeclarationKind
        get() = LezerDeclarationKind.of(this)

    override fun getNameIdentifier(): PsiElement? = when (this) {
        is LezerParameter, is LezerDialect -> firstChild
        // `@external prop name as alias from "..."` declares the alias
        is LezerExternalPropDeclaration -> simpleNameList.lastOrNull()
        else -> children.firstOrNull { it is LezerRuleName || it is LezerPrecedenceName || it is LezerSimpleName }
    }

    override fun getName(): String? = nameIdentifier?.text

    override fun setName(name: String): PsiElement {
        nameIdentifier?.let { LezerElementFactory.renameIdentifier(it, name) }
        return this
    }

    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: super.getTextOffset()

    override fun getIcon(flags: Int): Icon = kind.icon

    override fun getPresentation(): ItemPresentation = object : ItemPresentation {
        override fun getPresentableText(): String? = name?.let { it + parameterSuffix() }
        override fun getLocationString(): String? = null
        override fun getIcon(unused: Boolean): Icon = kind.icon
    }

    private fun parameterSuffix(): String {
        val params = (this as? LezerRuleDeclaration)?.paramList ?: (this as? LezerTopRuleDeclaration)?.paramList
        return params?.parameterList?.joinToString(", ", "<", ">") { it.text }.orEmpty()
    }

    override fun getUseScope(): SearchScope {
        if (this is LezerParameter) {
            PsiTreeUtil.getParentOfType(this, LezerRuleDeclaration::class.java, LezerTopRuleDeclaration::class.java)
                ?.let { return LocalSearchScope(it) }
        }
        val file = LocalSearchScope(containingFile)
        // Rules and tokens can also be used by code that uses the generated parser
        return if (kind.isGlobalRule) file.union(GlobalSearchScope.projectScope(project)) else file
    }

    /** Also removes separating commas of list items and trailing whitespace, so the remaining code stays valid. */
    override fun delete() {
        if (this is LezerPrecedence || this is LezerDialect || this is LezerExternalToken || this is LezerParameter) {
            val nextComma = PsiTreeUtil.skipWhitespacesAndCommentsForward(this)?.takeIf { it.elementType == LezerTypes.COMMA }
            if (nextComma != null) {
                val end = (nextComma.nextSibling as? PsiWhiteSpace) ?: nextComma
                parent.deleteChildRange(this, end)
                return
            }
            val prevComma = PsiTreeUtil.skipWhitespacesAndCommentsBackward(this)?.takeIf { it.elementType == LezerTypes.COMMA }
            if (prevComma != null) {
                parent.deleteChildRange(prevComma, this)
                return
            }
        }
        val trailingWhitespace = nextSibling as? PsiWhiteSpace
        if (trailingWhitespace != null) parent.deleteChildRange(this, trailingWhitespace) else super.delete()
    }
}
