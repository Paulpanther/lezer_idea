package de.paulmethfessel.lezer.generator.run

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.editor.toolbar.floating.FloatingToolbarProvider

/**
 * Shows the "regenerate on save" checkbox in the top right corner of grammar editors, like the open in browser icons.
 * The action hides itself in other editors, and a toolbar without visible actions isn't shown. Overriding `isApplicable`
 * instead is deprecated from 2026.2 on, and its replacement doesn't exist before.
 */
class GenerateOnSaveFloatingToolbarProvider : FloatingToolbarProvider {
    override val actionGroup: ActionGroup by lazy {
        DefaultActionGroup(ActionManager.getInstance().getAction("Lezer.GenerateOnSaveToggle"))
    }
}
