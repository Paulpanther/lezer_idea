package de.paulmethfessel.lezer.generator

import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.notification.NotificationType
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Files
import java.nio.file.Path

/** Installs `@lezer/generator` with npm, either into the grammar's package or globally. */
object GeneratorInstaller {
    fun install(project: Project, grammarFile: VirtualFile?, global: Boolean, onFinished: () -> Unit = {}) {
        val node = NodeLocator.find(project)
        val npm = node?.npm
        if (node == null || npm == null) {
            notify(project, "Cannot install $PACKAGE: ${if (node == null) "Node.js" else "npm"} was not found.", NotificationType.ERROR)
            return
        }

        val directory = if (global) null else GeneratorLocator.localInstallDirectory(project, grammarFile) ?: run {
            notify(project, "Cannot install $PACKAGE: no directory to install it into.", NotificationType.ERROR)
            return
        }
        if (directory != null && !Files.isRegularFile(directory.resolve("package.json"))) {
            val answer = Messages.showYesNoDialog(
                project,
                "There is no package.json in $directory, npm will create one. Install $PACKAGE there?",
                "Install $PACKAGE",
                Messages.getQuestionIcon(),
            )
            if (answer != Messages.YES) return
        }

        val args = listOf("install", if (global) "--global" else "--save-dev", PACKAGE)
        object : Task.Backgroundable(project, "Installing $PACKAGE", true) {
            override fun run(indicator: ProgressIndicator) {
                val commandLine = node.commandLine(npm, *args.toTypedArray())
                directory?.let { commandLine.withWorkDirectory(it.toFile()) }
                val output = CapturingProcessHandler(commandLine).runProcessWithProgressIndicator(indicator, 5 * 60_000)
                when {
                    output.isCancelled -> {}
                    output.isTimeout -> notify(project, "Installing $PACKAGE timed out.", NotificationType.ERROR)
                    output.exitCode != 0 -> notify(
                        project,
                        "npm ${args.joinToString(" ")} failed:<br><pre>${escape(output.stderr.ifBlank { output.stdout }.takeLast(2000))}</pre>",
                        NotificationType.ERROR,
                    )
                    else -> {
                        directory?.let { refresh(it) }
                        GeneratorLocator.invalidate()
                        val generator = GeneratorLocator.resolve(
                            project, grammarFile, if (global) GeneratorMode.GLOBAL else GeneratorMode.LOCAL, null,
                        )
                        notify(project, "Installed ${generator?.presentableText ?: PACKAGE}", NotificationType.INFORMATION)
                    }
                }
            }

            override fun onFinished() {
                LezerGeneratorRefresher.refresh(project)
                onFinished()
            }
        }.queue()
    }

    private fun refresh(directory: Path) {
        val paths = listOf(directory.resolve("package.json"), directory.resolve("package-lock.json"), directory.resolve("node_modules"))
        LocalFileSystem.getInstance().refreshNioFiles(paths, true, false, null)
    }

    private fun notify(project: Project, content: String, type: NotificationType) {
        LezerNotifications.group().createNotification(content, type).notify(project)
    }

    private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private const val PACKAGE = GeneratorLocator.PACKAGE
}
