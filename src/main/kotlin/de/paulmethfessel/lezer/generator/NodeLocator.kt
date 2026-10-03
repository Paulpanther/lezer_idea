package de.paulmethfessel.lezer.generator

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.EnvironmentUtil
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isExecutable
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/** Provides the node interpreter configured elsewhere in the IDE, e.g. by the JavaScript plugin. */
interface LezerNodeInterpreterProvider {
    /** Where the interpreter comes from, shown in the settings. */
    val sourceName: String

    fun findNode(project: Project): Path?

    companion object {
        val EP_NAME = ExtensionPointName<LezerNodeInterpreterProvider>("de.paulmethfessel.lezer.nodeInterpreterProvider")
    }
}

data class NodeInstallation(val node: Path) {
    /** npm next to node (as installed by node, nvm, Homebrew, ...), otherwise from the PATH. */
    val npm: Path?
        get() {
            val names = if (SystemInfo.isWindows) listOf("npm.cmd", "npm.exe") else listOf("npm")
            names.map { node.resolveSibling(it) }.firstOrNull { Files.isRegularFile(it) }?.let { return it }
            return NodeLocator.findInPath(if (SystemInfo.isWindows) "npm.cmd" else "npm")
        }

    /**
     * A command line with node's directory on the PATH, so that scripts with a `#!/usr/bin/env node` shebang (like
     * npm) work even if the IDE was not started from a shell.
     */
    fun commandLine(executable: Path, vararg args: String): GeneralCommandLine {
        val path = listOfNotNull(node.parent?.toString(), EnvironmentUtil.getValue("PATH")).joinToString(File.pathSeparator)
        return GeneralCommandLine(executable.toString(), *args)
            .withEnvironment("PATH", path)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
            .withCharset(StandardCharsets.UTF_8)
    }

    fun nodeCommandLine(vararg args: String): GeneralCommandLine = commandLine(node, *args)
}

object NodeLocator {
    /** The node interpreter from the settings, another plugin or the system, in this order. */
    fun find(project: Project): NodeInstallation? {
        LezerSettings.getInstance(project).state.nodePath?.takeIf { it.isNotBlank() }?.let {
            val path = Path(it)
            return if (Files.isRegularFile(path)) NodeInstallation(path) else null
        }
        for (provider in LezerNodeInterpreterProvider.EP_NAME.extensionList) {
            provider.findNode(project)?.let { return NodeInstallation(it) }
        }
        return detect()?.let { NodeInstallation(it) }
    }

    /** Searches the PATH of the login shell, then locations used by common installers and version managers. */
    fun detect(): Path? {
        findInPath(executableName)?.let { return it }
        return candidateDirectories(Path(System.getProperty("user.home")), System::getenv, SystemInfo.isWindows)
            .map { it.resolve(executableName) }
            .firstOrNull { it.isExecutable() }
    }

    /** The executable [name] in a directory of the PATH, by default the one of the login shell. */
    internal fun findInPath(name: String, path: String? = EnvironmentUtil.getValue("PATH")): Path? =
        path.orEmpty().split(File.pathSeparatorChar).asSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { dir -> runCatching { Path(dir).resolve(name) }.getOrNull() }
            .firstOrNull { Files.isRegularFile(it) && it.isExecutable() }

    private val executableName get() = if (SystemInfo.isWindows) "node.exe" else "node"

    internal fun candidateDirectories(home: Path, env: (String) -> String?, isWindows: Boolean): List<Path> {
        val dirs = mutableListOf<Path>()
        if (isWindows) {
            env("ProgramFiles")?.let { dirs.add(Path(it, "nodejs")) }
            env("APPDATA")?.let { dirs.addAll(newestVersion(Path(it, "nvm"))) }
        } else {
            dirs.add(Path("/opt/homebrew/bin"))
            dirs.add(Path("/usr/local/bin"))
            dirs.add(Path("/usr/bin"))
            dirs.addAll(newestVersion(home.resolve(".nvm/versions/node")).map { it.resolve("bin") })
            dirs.add(home.resolve(".volta/bin"))
            dirs.addAll(newestVersion(home.resolve(".local/share/fnm/node-versions")).map { it.resolve("installation/bin") })
        }
        return dirs
    }

    /** Version directories like `v22.1.0`, newest first. */
    internal fun newestVersion(dir: Path): List<Path> {
        if (!dir.isDirectory()) return emptyList()
        return dir.listDirectoryEntries()
            .filter { it.isDirectory() && it.name.removePrefix("v").firstOrNull()?.isDigit() == true }
            .sortedWith(compareByDescending<Path> { it.name.version(0) }
                .thenByDescending { it.name.version(1) }
                .thenByDescending { it.name.version(2) })
    }

    private fun String.version(part: Int): Int = removePrefix("v").split('.').getOrNull(part)?.toIntOrNull() ?: 0
}
