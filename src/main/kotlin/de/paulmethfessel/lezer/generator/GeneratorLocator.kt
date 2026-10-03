package de.paulmethfessel.lezer.generator

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path
import kotlin.io.path.readText

enum class GeneratorKind(val displayName: String) {
    LOCAL("local"),
    GLOBAL("global"),
    CUSTOM("custom"),
}

/**
 * An installed `@lezer/generator` package, with its command line interface script ([cli], the `bin` of its
 * package.json) and its ES module ([module], from `exports`).
 */
data class LezerGenerator(
    val packageDir: Path,
    val version: String?,
    val kind: GeneratorKind,
    val cli: Path,
    val module: Path,
) {
    val presentableText: String
        get() = "@lezer/generator ${version ?: "(unknown version)"}, ${kind.displayName}: $packageDir"
}

object GeneratorLocator {
    const val PACKAGE = "@lezer/generator"

    private val LOG = logger<GeneratorLocator>()

    /** Global `node_modules` directories by npm executable, as reported by `npm root -g`. */
    private val globalRoots = ConcurrentHashMap<Path, Path>()
    private val pendingGlobalRoots = ConcurrentHashMap.newKeySet<Path>()

    /** Resolves the generator with the project's settings. */
    fun resolve(project: Project, grammarFile: VirtualFile?, allowProcess: Boolean = canRunProcess()): LezerGenerator? {
        val settings = LezerSettings.getInstance(project).state
        return resolve(project, grammarFile, settings.generator, settings.customGeneratorPath, allowProcess)
    }

    /**
     * Finds the generator for [grammarFile]. Finding a global installation runs `npm root -g` once per npm, if
     * [allowProcess] is false a guess based on the location of node is used until that has finished in the background.
     */
    fun resolve(
        project: Project,
        grammarFile: VirtualFile?,
        mode: GeneratorMode,
        customPath: String?,
        allowProcess: Boolean = canRunProcess(),
    ): LezerGenerator? = when (mode) {
        GeneratorMode.AUTO -> findLocal(project, grammarFile) ?: findGlobal(project, allowProcess)
        GeneratorMode.LOCAL -> findLocal(project, grammarFile)
        GeneratorMode.GLOBAL -> findGlobal(project, allowProcess)
        GeneratorMode.CUSTOM -> customPath?.takeIf { it.isNotBlank() }?.let { load(Path(it), GeneratorKind.CUSTOM) }
    }

    /** Searches `node_modules` from the grammar's directory up to its content root (or the project directory). */
    fun findLocal(project: Project, grammarFile: VirtualFile?): LezerGenerator? =
        searchDirectories(project, grammarFile).firstNotNullOfOrNull { dir ->
            load(dir.resolve("node_modules").resolve(PACKAGE), GeneratorKind.LOCAL)
        }

    fun findGlobal(project: Project, allowProcess: Boolean = canRunProcess()): LezerGenerator? {
        val node = NodeLocator.find(project) ?: return null
        val root = globalRoot(node, allowProcess) ?: return null
        return load(root.resolve(PACKAGE), GeneratorKind.GLOBAL)
    }

    /** Where `npm install -D` should run: the closest directory with a package.json, otherwise the content root. */
    fun localInstallDirectory(project: Project, grammarFile: VirtualFile?): Path? {
        val dirs = searchDirectories(project, grammarFile)
        return dirs.firstOrNull { Files.isRegularFile(it.resolve("package.json")) } ?: dirs.lastOrNull()
    }

    /** Forgets cached global installation directories, e.g. after installing or changing the settings. */
    fun invalidate() {
        globalRoots.clear()
    }

    fun load(packageDir: Path, kind: GeneratorKind): LezerGenerator? {
        val packageJson = packageDir.resolve("package.json")
        if (!Files.isRegularFile(packageJson)) return null
        val json = runCatching { JsonParser.parseString(packageJson.readText()).asJsonObject }.getOrNull()
        val version = json?.string("version")
        // `bin` is a path or a map of command names to paths
        val cli = json?.get("bin")?.let { bin -> if (bin.isJsonObject) bin.asJsonObject.string("lezer-generator") else bin.stringOrNull() }
        val module = json?.let(::esModule)
        return LezerGenerator(
            packageDir, version, kind,
            cli = packageDir.resolve(cli ?: "src/lezer-generator.cjs").normalize(),
            module = packageDir.resolve(module ?: "dist/index.js").normalize(),
        )
    }

    /** The file that `import "@lezer/generator"` loads: `exports` (string or conditions), then `module` and `main`. */
    private fun esModule(json: JsonObject): String? {
        val exports = json.get("exports")?.let { if (it.isJsonObject && it.asJsonObject.has(".")) it.asJsonObject.get(".") else it }
        val export = when {
            exports == null -> null
            exports.isJsonObject -> exports.asJsonObject.let { it.string("import") ?: it.string("default") }
            else -> exports.stringOrNull()
        }
        return export ?: json.string("module") ?: json.string("main")
    }

    private fun JsonObject.string(name: String): String? = get(name)?.stringOrNull()

    private fun JsonElement.stringOrNull(): String? = takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    private fun searchDirectories(project: Project, grammarFile: VirtualFile?): List<Path> =
        ApplicationManager.getApplication().runReadAction(Computable { searchDirectoriesInReadAction(project, grammarFile) })

    private fun searchDirectoriesInReadAction(project: Project, grammarFile: VirtualFile?): List<Path> {
        val start = grammarFile?.parent ?: project.guessProjectDir() ?: return emptyList()
        val root = grammarFile?.let { ProjectFileIndex.getInstance(project).getContentRootForFile(it) }
            ?: project.guessProjectDir()
        val dirs = mutableListOf<Path>()
        var dir: VirtualFile? = start
        while (dir != null) {
            dir.fileSystem.getNioPath(dir)?.let { dirs.add(it) }
            if (dir == root) break
            dir = dir.parent
        }
        return dirs
    }

    private fun globalRoot(node: NodeInstallation, allowProcess: Boolean): Path? {
        val npm = node.npm ?: return defaultGlobalRoot(node)
        globalRoots[npm]?.let { return it }
        if (allowProcess) {
            return queryGlobalRoot(node, npm)?.also { globalRoots[npm] = it } ?: defaultGlobalRoot(node)
        }
        if (pendingGlobalRoots.add(npm)) {
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    val root = queryGlobalRoot(node, npm)
                    if (root != null) {
                        globalRoots[npm] = root
                        if (root != defaultGlobalRoot(node)) LezerGeneratorRefresher.refreshAll()
                    }
                } finally {
                    pendingGlobalRoots.remove(npm)
                }
            }
        }
        return defaultGlobalRoot(node)
    }

    private fun queryGlobalRoot(node: NodeInstallation, npm: Path): Path? = try {
        val output = CapturingProcessHandler(node.commandLine(npm, "root", "-g")).runProcess(10_000)
        output.stdout.trim().takeIf { output.exitCode == 0 && !output.isTimeout && it.isNotEmpty() }?.let { Path(it) }
    } catch (e: Exception) {
        LOG.info("Could not run npm root -g", e)
        null
    }

    /** npm's default global prefix is node's installation directory. */
    internal fun defaultGlobalRoot(node: NodeInstallation, isWindows: Boolean = SystemInfo.isWindows): Path? {
        val bin = node.node.parent ?: return null
        return if (isWindows) bin.resolve("node_modules") else bin.parent?.resolve("lib/node_modules")
    }

    private fun canRunProcess(): Boolean {
        val app = ApplicationManager.getApplication()
        return !app.isDispatchThread && !app.isReadAccessAllowed
    }
}
