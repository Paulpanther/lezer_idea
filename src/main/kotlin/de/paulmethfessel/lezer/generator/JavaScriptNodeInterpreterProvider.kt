package de.paulmethfessel.lezer.generator

import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager
import com.intellij.javascript.nodejs.interpreter.local.NodeJsLocalInterpreter
import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path

/** The project's Node.js interpreter from the JavaScript plugin, only registered if that plugin is installed. */
class JavaScriptNodeInterpreterProvider : LezerNodeInterpreterProvider {
    override val sourceName: String get() = "Node.js settings"

    override fun findNode(project: Project): Path? {
        // Remote and WSL interpreters are not supported, the plugin's own detection is used for them
        val interpreter = NodeJsLocalInterpreter.tryCast(NodeJsInterpreterManager.getInstance(project).interpreter) ?: return null
        return Path(interpreter.interpreterSystemDependentPath).takeIf { Files.isRegularFile(it) }
    }
}
