package de.paulmethfessel.lezer.generator.run

import com.intellij.execution.ExecutionException
import com.intellij.execution.RunManager
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import de.paulmethfessel.lezer.LezerFileType
import java.util.concurrent.ConcurrentHashMap

/** Runs the generator configurations with "generate on save" when their grammar is saved. */
class GenerateOnSaveListener : BulkFileListener {
    override fun after(events: List<VFileEvent>) {
        val saved = events.filter { it is VFileContentChangeEvent && it.isFromSave && it.file.fileType == LezerFileType }
            .mapNotNull { it.file }
        if (saved.isEmpty()) return
        for (project in ProjectManager.getInstance().openProjects) {
            if (project.isDisposed) continue
            RunManager.getInstance(project).getConfigurationsList(LezerGeneratorConfigurationType.getInstance())
                .filterIsInstance<LezerGeneratorRunConfiguration>()
                .filter { it.options.generateOnSave && it.grammarVirtualFile() in saved }
                .forEach { GenerateOnSave.run(project, it) }
        }
    }
}

object GenerateOnSave {
    private const val TIMEOUT_MS = 60_000

    /** Configurations that are being generated, saving again meanwhile doesn't start another run. */
    private val running = ConcurrentHashMap.newKeySet<LezerGeneratorRunConfiguration>()
    private val FAILURE = Key.create<Notification>("lezer.generateOnSave.failure")

    fun run(project: Project, configuration: LezerGeneratorRunConfiguration) {
        if (!running.add(configuration)) return
        object : Task.Backgroundable(project, "Generating parser: ${configuration.name}", true) {
            override fun run(indicator: ProgressIndicator) {
                val error = try {
                    val output = CapturingProcessHandler(configuration.createCommandLine())
                        .runProcessWithProgressIndicator(indicator, TIMEOUT_MS)
                    when {
                        output.isCancelled -> return
                        output.isTimeout -> "timed out"
                        output.exitCode != 0 -> output.stderr.ifBlank { output.stdout }
                        else -> null
                    }
                } catch (e: ExecutionException) {
                    e.message
                }
                configuration.getUserData(FAILURE)?.expire()
                if (error == null) {
                    configuration.refreshOutput()
                } else {
                    val notification = NotificationGroupManager.getInstance().getNotificationGroup("Lezer Generator")
                        .createNotification(
                            "Generating ${configuration.name} failed",
                            "<pre>${StringUtil.escapeXmlEntities(error.trim().take(2000))}</pre>",
                            NotificationType.ERROR,
                        )
                    configuration.putUserData(FAILURE, notification)
                    notification.notify(project)
                }
            }

            override fun onFinished() {
                running.remove(configuration)
            }
        }.queue()
    }
}
