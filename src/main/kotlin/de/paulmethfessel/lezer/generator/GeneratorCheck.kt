package de.paulmethfessel.lezer.generator

import com.google.gson.Gson
import com.intellij.execution.ExecutionException
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.progress.ProgressIndicator

/** Result of running the generator on a grammar, see `lezer/lezer-check.cjs`. */
class GeneratorReport(
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    /** An unexpected error of the generator or the check script. */
    val failure: String? = null,
) {
    /** The script's JSON. Gson doesn't call constructors, so missing properties are null even if Kotlin says otherwise. */
    private class Json(val errors: List<String>?, val warnings: List<String>?, val failure: String?)

    companion object {
        fun parse(json: String): GeneratorReport {
            val report = Gson().fromJson(json, Json::class.java)
                ?: return GeneratorReport(failure = "lezer-generator produced no output")
            return GeneratorReport(report.errors.orEmpty(), report.warnings.orEmpty(), report.failure)
        }
    }
}

/** Runs `@lezer/generator` on a grammar without writing the generated parser anywhere. */
object GeneratorCheck {
    /** The file name used in the generator's messages, see [GeneratorMessage]. */
    const val FILE_NAME = "input"

    private const val TIMEOUT_MS = 30_000

    @Throws(ExecutionException::class)
    fun run(node: NodeInstallation, generator: LezerGenerator, text: String, indicator: ProgressIndicator): GeneratorReport {
        val commandLine = node.nodeCommandLine(NodeScripts.extract("lezer-check.cjs").toString(), generator.packageDir.toString(), FILE_NAME)
        val handler = CapturingProcessHandler(commandLine)
        handler.processInput.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        val output = handler.runProcessWithProgressIndicator(indicator, TIMEOUT_MS)
        return when {
            output.isCancelled -> GeneratorReport()
            output.isTimeout -> GeneratorReport(failure = "lezer-generator did not finish within ${TIMEOUT_MS / 1000} seconds")
            output.exitCode != 0 -> GeneratorReport(failure = output.stderr.ifBlank { "exit code ${output.exitCode}" })
            else -> GeneratorReport.parse(output.stdout)
        }
    }
}
