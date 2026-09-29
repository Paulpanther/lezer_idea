package de.paulmethfessel.lezer.editor

import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler
import de.paulmethfessel.lezer.psi.LezerTypes

class LezerQuoteHandler : SimpleTokenSetQuoteHandler(LezerTypes.STRING)
