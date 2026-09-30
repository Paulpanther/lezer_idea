package de.paulmethfessel.lezer.generator.run

import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.LocatableConfigurationBase
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import de.paulmethfessel.lezer.generator.GeneratorInstaller
import de.paulmethfessel.lezer.generator.GeneratorLocator
import de.paulmethfessel.lezer.generator.LezerGenerator
import de.paulmethfessel.lezer.generator.NodeLocator
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path

class LezerGeneratorRunConfiguration(project: Project, factory: ConfigurationFactory, name: String) :
    LocatableConfigurationBase<LezerGeneratorOptions>(project, factory, name) {

    public override fun getOptions(): LezerGeneratorOptions = super.getOptions() as LezerGeneratorOptions

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> = LezerGeneratorSettingsEditor(project)

    override fun suggestedName(): String? = options.grammarFile?.let { "Generate ${Path(it).fileName}" }

    override fun checkConfiguration() {
        val grammar = options.grammarFile
        if (grammar.isNullOrBlank()) throw RuntimeConfigurationError("The grammar file is not specified")
        if (!Files.isRegularFile(resolve(grammar))) throw RuntimeConfigurationError("The grammar file does not exist")
        if (NodeLocator.find(project) == null) throw RuntimeConfigurationError("Node.js was not found")
        if (findGenerator() == null) {
            throw RuntimeConfigurationError("@lezer/generator was not found").apply {
                setQuickFix(Runnable { GeneratorInstaller.install(project, grammarVirtualFile(), global = false) })
            }
        }
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
        object : CommandLineState(environment) {
            override fun startProcess(): ProcessHandler {
                val handler = KillableColoredProcessHandler(createCommandLine())
                ProcessTerminatedListener.attach(handler)
                handler.addProcessListener(object : ProcessListener {
                    override fun processTerminated(event: ProcessEvent) = refreshOutput()
                })
                return handler
            }
        }

    @Throws(ExecutionException::class)
    fun createCommandLine(): GeneralCommandLine {
        val node = NodeLocator.find(project) ?: throw ExecutionException("Node.js was not found, configure it in Settings | Languages & Frameworks | Lezer Grammar")
        val generator = findGenerator() ?: throw ExecutionException("@lezer/generator was not found, install it with npm i -D @lezer/generator")
        return node.nodeCommandLine(generator.cli.toString(), *GeneratorArguments.of(options).toTypedArray())
            .withWorkDirectory(workingDirectory().toFile())
    }

    private fun findGenerator(): LezerGenerator? {
        val file = grammarVirtualFile()
        val mode = options.generatorMode ?: return GeneratorLocator.resolve(project, file)
        return GeneratorLocator.resolve(project, file, mode, options.customGeneratorPath)
    }

    private fun refreshOutput() {
        val files = GeneratorArguments.outputFiles(options).map { resolve(it) }
        if (files.isNotEmpty()) LocalFileSystem.getInstance().refreshNioFiles(files, true, false, null)
    }

    private fun grammarVirtualFile(): VirtualFile? =
        options.grammarFile?.takeIf { it.isNotBlank() }?.let { LocalFileSystem.getInstance().findFileByNioFile(resolve(it)) }

    /** The configured directory, otherwise the grammar's directory, otherwise the project directory. */
    private fun workingDirectory(): Path {
        options.workingDirectory?.takeIf { it.isNotBlank() }?.let { return Path(it) }
        val grammar = options.grammarFile?.takeIf { it.isNotBlank() }?.let { Path(it) }
        if (grammar != null && grammar.isAbsolute) return grammar.parent
        return Path(project.basePath ?: ".")
    }

    private fun resolve(path: String): Path = workingDirectory().resolve(path)
}
