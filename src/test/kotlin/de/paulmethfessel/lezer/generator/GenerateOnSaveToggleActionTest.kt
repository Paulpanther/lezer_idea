package de.paulmethfessel.lezer.generator

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.paulmethfessel.lezer.generator.run.GenerateOnSaveToggleAction

/** The floating toolbar relies on the action hiding itself outside grammar editors. */
class GenerateOnSaveToggleActionTest : BasePlatformTestCase() {
    fun testVisibleOnlyInGrammarEditors() {
        val grammar = myFixture.configureByText("lang.grammar", "@top P { \"x\" }").virtualFile
        assertTrue(isVisible(grammar, EditorKind.MAIN_EDITOR))
        // Diffs and other embedded editors don't get the checkbox
        assertFalse(isVisible(grammar, EditorKind.DIFF))

        val text = myFixture.configureByText("notes.txt", "text").virtualFile
        assertFalse(isVisible(text, EditorKind.MAIN_EDITOR))
    }

    private fun isVisible(file: VirtualFile, kind: EditorKind): Boolean {
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        val editor = EditorFactory.getInstance().createEditor(document, project, kind)
        try {
            val dataContext = SimpleDataContext.builder()
                .add(CommonDataKeys.PROJECT, project)
                .add(CommonDataKeys.EDITOR, editor)
                .add(CommonDataKeys.VIRTUAL_FILE, file)
                .build()
            val action = GenerateOnSaveToggleAction()
            val event = TestActionEvent.createTestEvent(action, dataContext)
            action.update(event)
            return event.presentation.isEnabledAndVisible
        } finally {
            EditorFactory.getInstance().releaseEditor(editor)
        }
    }
}
