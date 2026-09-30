package de.paulmethfessel.lezer.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import de.paulmethfessel.lezer.LezerFileType

object LezerElementFactory {
    /** Creates an identifier token (`NAME` or a contextual keyword) with the given text. */
    fun createIdentifier(project: Project, name: String): PsiElement {
        val file = PsiFileFactory.getInstance(project)
            .createFileFromText("dummy.grammar", LezerFileType, "$name {}") as LezerFile
        val declaration = PsiTreeUtil.getChildOfType(file, LezerRuleDeclaration::class.java)
            ?: error("Invalid identifier: $name")
        return declaration.ruleName.firstChild
    }

    /** Replaces the identifier token in [nameElement] (a name element or the token itself) and returns the new token. */
    fun renameIdentifier(nameElement: PsiElement, newName: String): PsiElement {
        val identifier = PsiTreeUtil.getDeepestFirst(nameElement)
        return identifier.replace(createIdentifier(nameElement.project, newName))
    }
}
