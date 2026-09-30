package de.paulmethfessel.lezer.hints

import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.elementType
import de.paulmethfessel.lezer.psi.LezerPrecedence
import de.paulmethfessel.lezer.psi.LezerPrecedenceBody
import de.paulmethfessel.lezer.psi.LezerPrecedenceMarker
import de.paulmethfessel.lezer.psi.LezerTypes
import de.paulmethfessel.lezer.resolve.LezerResolver

/** Shows the rank and associativity of the precedence after markers like `!times`: `!times #2 left`. */
class LezerPrecedenceInlayHintsProvider : InlayHintsProvider, DumbAware {
    override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector = Collector()

    private class Collector : SharedBypassCollector {
        override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
            if (element !is LezerPrecedenceMarker) return
            val name = element.precedenceName ?: return
            val precedence = LezerResolver.resolve(name) as? LezerPrecedence ?: return
            val all = (precedence.parent as? LezerPrecedenceBody)?.precedenceList ?: return
            val rank = all.indexOf(precedence) + 1
            val associativity = associativity(precedence)
            val tooltip = "Precedence $rank of ${all.size} (highest first)" + associativity?.let { ", $it" }.orEmpty()
            sink.addPresentation(InlineInlayPosition(element.textRange.endOffset, true), null, tooltip, HintFormat.default) {
                text(listOfNotNull("#$rank", associativity).joinToString(" "))
            }
        }

        private fun associativity(precedence: LezerPrecedence): String? = when (precedence.lastChild.elementType) {
            LezerTypes.AT_LEFT -> "left"
            LezerTypes.AT_RIGHT -> "right"
            LezerTypes.AT_CUT -> "cut"
            else -> null
        }
    }
}
