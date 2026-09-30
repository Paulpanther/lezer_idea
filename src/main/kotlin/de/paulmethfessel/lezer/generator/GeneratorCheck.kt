package de.paulmethfessel.lezer.generator

import com.google.gson.Gson
import com.intellij.execution.ExecutionException
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.progress.ProgressIndicator
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

/** Result of running the generator on a grammar, see `lezer/lezer-check.cjs`. */
class GeneratorReport(
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    /** An unexpected error of the generator or the check script. */
    val failure: String? = null,
)

/** Runs `@lezer/generator` on a grammar without writing the generated parser anywhere. */
object GeneratorCheck {
    /** The file name used in the generator's messages, see [GeneratorMessage]. */
    const val FILE_NAME = "input"

    private const val TIMEOUT_MS = 30_000

    @Throws(ExecutionException::class)
    fun run(node: NodeInstallation, generator: LezerGenerator, text: String, indicator: ProgressIndicator): GeneratorReport {
        val commandLine = node.nodeCommandLine(script().toString(), generator.packageDir.toString(), FILE_NAME)
        val handler = CapturingProcessHandler(commandLine)
        handler.processInput.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        val output = handler.runProcessWithProgressIndicator(indicator, TIMEOUT_MS)
        return when {
            output.isCancelled -> GeneratorReport()
            output.isTimeout -> GeneratorReport(failure = "lezer-generator did not finish within ${TIMEOUT_MS / 1000} seconds")
            output.exitCode != 0 -> GeneratorReport(failure = output.stderr.ifBlank { "exit code ${output.exitCode}" })
            else -> Gson().fromJson(output.stdout, GeneratorReport::class.java)
                ?: GeneratorReport(failure = "lezer-generator produced no output")
        }
    }

    /** The check script, copied from the plugin's resources to a file that node can run. */
    @Synchronized
    private fun script(): Path {
        val content = GeneratorCheck::class.java.getResourceAsStream("/lezer/lezer-check.cjs")!!.use { it.readBytes() }
        val file = Path.of(PathManager.getSystemPath(), "lezer", "lezer-check.cjs")
        if (!Files.isRegularFile(file) || !file.readBytes().contentEquals(content)) {
            file.parent.createDirectories()
            file.writeBytes(content)
        }
        return file
    }
}
