package de.paulmethfessel.lezer.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import de.paulmethfessel.lezer.documentation.LezerDocComments
import de.paulmethfessel.lezer.psi.LezerDeclarationKind
import de.paulmethfessel.lezer.psi.LezerFile
import de.paulmethfessel.lezer.psi.LezerNamedElement
import de.paulmethfessel.lezer.psi.LezerProps
import de.paulmethfessel.lezer.psi.LezerRuleName
import de.paulmethfessel.lezer.resolve.LezerResolver

/**
 * Rules and tokens that are never referenced, except from within themselves. Top rules, inline rules and external
 * tokens are always used, and rules with an `@export` prop are used from code.
 */
class LezerUnusedDeclarationInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : PsiElementVisitor() {
        override fun visitElement(element: PsiElement) {
            if (element !is LezerNamedElement || !isCandidate(element)) return
            val file = element.containingFile as? LezerFile ?: return
            if (isUsed(file, element)) return
            val name = element.nameIdentifier ?: return
            val what = if (element.kind.isToken) "Token" else "Rule"
            holder.registerProblem(
                name,
                "$what '${element.name}' is never used",
                ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                RemoveDeclarationFix(what.lowercase(), element.name.orEmpty()),
            )
        }
    }

    private fun isCandidate(element: LezerNamedElement): Boolean = when (element.kind) {
        LezerDeclarationKind.TERM, LezerDeclarationKind.TERM_TEMPLATE,
        LezerDeclarationKind.TOKEN, LezerDeclarationKind.TOKEN_TEMPLATE -> !isExported(element)
        else -> false
    }

    private fun isExported(element: LezerNamedElement): Boolean =
        PsiTreeUtil.getChildOfType(element, LezerProps::class.java)?.propList.orEmpty().any { it.firstChild.text == "@export" }

    /** Used if referenced from outside its own declaration, so recursive rules aren't used by themselves. */
    private fun isUsed(file: LezerFile, declaration: LezerNamedElement): Boolean =
        references(file)[declaration].orEmpty().any { !PsiTreeUtil.isAncestor(declaration, it, false) }

    /** All rule references of the file by their target, resolved once per file modification. */
    private fun references(file: LezerFile): Map<LezerNamedElement, List<PsiElement>> = CachedValuesManager.getCachedValue(file) {
        val references = PsiTreeUtil.findChildrenOfType(file, LezerRuleName::class.java)
            .filter { LezerResolver.namespaceOf(it) == LezerResolver.Namespace.RULE }
            .mapNotNull { name -> LezerResolver.resolve(name)?.let { it to name as PsiElement } }
            .groupBy({ it.first }, { it.second })
        CachedValueProvider.Result.create(references, file)
    }

    private class RemoveDeclarationFix(private val what: String, private val name: String) : LocalQuickFix {
        override fun getFamilyName(): String = "Remove unused declaration"

        override fun getName(): String = "Remove unused $what '$name'"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val declaration = descriptor.psiElement.parent as? LezerNamedElement ?: return
            // Its documentation goes with it
            val comments = LezerDocComments.commentsBefore(declaration)
            if (comments.isNotEmpty()) {
                val end = (declaration.prevSibling as? PsiWhiteSpace) ?: comments.last()
                declaration.parent.deleteChildRange(comments.first(), end)
            }
            declaration.delete()
        }
    }
}
