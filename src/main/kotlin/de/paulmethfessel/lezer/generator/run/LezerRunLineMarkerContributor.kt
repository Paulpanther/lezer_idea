package de.paulmethfessel.lezer.generator.run

import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import de.paulmethfessel.lezer.psi.LezerTopRuleDeclaration
import de.paulmethfessel.lezer.psi.LezerTypes

/** A "generate parser" icon on the first `@top`, the generator always processes the whole file. */
class LezerRunLineMarkerContributor : RunLineMarkerContributor() {
    override fun getInfo(element: PsiElement): Info? {
        if (element.elementType != LezerTypes.AT_TOP) return null
        val declaration = element.parent as? LezerTopRuleDeclaration ?: return null
        if (PsiTreeUtil.findChildOfType(element.containingFile, LezerTopRuleDeclaration::class.java) != declaration) return null
        return Info(AllIcons.Actions.Compile, ExecutorAction.getActions(0)) { "Generate parser" }
    }
}
