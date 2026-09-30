package de.paulmethfessel.lezer.generator

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.EditorNotifications

/** Updates everything that depends on the Node.js and generator installation after it changed. */
object LezerGeneratorRefresher {
    fun refresh(project: Project) {
        GeneratorLocator.invalidate()
        refreshViews(project)
    }

    fun refreshAll() {
        ProjectManager.getInstance().openProjects.forEach(::refreshViews)
    }

    private fun refreshViews(project: Project) {
        ApplicationManager.getApplication().invokeLater({
            EditorNotifications.getInstance(project).updateAllNotifications()
            DaemonCodeAnalyzer.getInstance(project).restart("Lezer generator installation changed")
        }, project.disposed)
    }
}
