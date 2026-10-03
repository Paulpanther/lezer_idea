package de.paulmethfessel.lezer.playground

import com.google.gson.Gson
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.concurrency.AppExecutorUtil
import de.paulmethfessel.lezer.generator.NodeInstallation
import de.paulmethfessel.lezer.generator.NodeScripts
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * The node process that builds parsers and parses the playground's input, see `lezer/lezer-playground.mjs`. It keeps
 * running, so only the first request pays for starting node and loading the generator.
 */
@Service(Service.Level.PROJECT)
class PlaygroundWorker : Disposable {
    private class Running(val node: NodeInstallation, val handler: OSProcessHandler, val output: WorkerOutput)

    private val gson = Gson()
    private val ids = AtomicLong()

    /** Requests are sent one at a time, so only this executor's thread touches [running]. */
    private val executor: ExecutorService = AppExecutorUtil.createBoundedApplicationPoolExecutor("Lezer Playground", 1)

    /** The newest request that hasn't been sent yet, older ones are dropped. */
    private val pending = AtomicReference<Pair<PlaygroundRequest, CompletableFuture<PlaygroundResult?>>?>()

    @Volatile
    private var running: Running? = null

    fun nextId(): Long = ids.incrementAndGet()

    /** Parses in the background. The future completes with null if the request was replaced by a newer one. */
    fun parse(node: NodeInstallation, request: PlaygroundRequest): CompletableFuture<PlaygroundResult?> {
        val future = CompletableFuture<PlaygroundResult?>()
        pending.getAndSet(request to future)?.second?.complete(null)
        executor.execute {
            val (next, nextFuture) = pending.getAndSet(null) ?: return@execute
            nextFuture.complete(runCatching { send(node, next) }.getOrElse { e ->
                LOG.info("Lezer Playground worker failed", e)
                stop()
                PlaygroundResult(id = next.id, grammarError = "The playground worker failed: ${e.message}")
            })
        }
        return future
    }

    private fun send(node: NodeInstallation, request: PlaygroundRequest): PlaygroundResult {
        val worker = running?.takeIf { it.node == node && !it.handler.isProcessTerminated } ?: start(node)
        try {
            worker.handler.processInput.apply {
                write((gson.toJson(request) + "\n").toByteArray(Charsets.UTF_8))
                flush()
            }
        } catch (e: IOException) {
            // It exited before reading the request, its error output says why
            worker.handler.waitFor(EXIT_WAIT_MS)
            throw exited(worker)
        }
        while (true) {
            val line = worker.output.lines.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (line == null) {
                stop()
                return PlaygroundResult(id = request.id, grammarError = "Parsing took longer than $TIMEOUT_SECONDS seconds")
            }
            if (line === WorkerOutput.EXITED) throw exited(worker)
            val result = PlaygroundResult.parse(gson, line)
            // Responses to requests that timed out before are skipped
            if (result.id == request.id) return result
        }
    }

    private fun exited(worker: Running) = IllegalStateException("The worker exited: ${worker.output.stderrTail()}")

    private fun start(node: NodeInstallation): Running {
        stop()
        val handler = OSProcessHandler(node.nodeCommandLine(NodeScripts.extract("lezer-playground.mjs").toString()))
        val output = WorkerOutput()
        handler.addProcessListener(output)
        handler.startNotify()
        return Running(node, handler, output).also { running = it }
    }

    /** Stops the worker, the next request starts a new one that re-imports all modules. */
    fun restart() {
        executor.execute { stop() }
    }

    private fun stop() {
        running?.handler?.destroyProcess()
        running = null
    }

    override fun dispose() {
        stop()
        executor.shutdownNow()
    }

    companion object {
        private val LOG = logger<PlaygroundWorker>()
        private const val TIMEOUT_SECONDS = 30L
        private const val EXIT_WAIT_MS = 5_000L

        fun getInstance(project: Project): PlaygroundWorker = project.service()
    }
}

/** Collects the worker's responses line by line, and the end of its error output to explain why it exited. */
internal class WorkerOutput : ProcessListener {
    /** Complete stdout lines, followed by [EXITED] when the process terminated. */
    val lines = LinkedBlockingQueue<String>()
    private val stdout = StringBuilder()
    private val stderr = ArrayDeque<String>()

    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
        when {
            ProcessOutputType.isStdout(outputType) -> synchronized(stdout) {
                // The text isn't necessarily split at line ends, responses can be long
                stdout.append(event.text)
                var end = stdout.indexOf("\n")
                while (end >= 0) {
                    lines.add(stdout.substring(0, end).removeSuffix("\r"))
                    stdout.delete(0, end + 1)
                    end = stdout.indexOf("\n")
                }
            }
            ProcessOutputType.isStderr(outputType) -> synchronized(stderr) {
                LOG.debug("Lezer Playground: ${event.text.trimEnd()}")
                stderr.addLast(event.text)
                while (stderr.size > MAX_STDERR_CHUNKS) stderr.removeFirst()
            }
        }
    }

    override fun processTerminated(event: ProcessEvent) {
        lines.add(EXITED)
    }

    fun stderrTail(): String = synchronized(stderr) { stderr.joinToString("").trim().takeLast(2000) }

    companion object {
        private val LOG = logger<WorkerOutput>()
        private const val MAX_STDERR_CHUNKS = 50

        /** Compared by identity, a response line is never this instance. */
        @Suppress("StringOperationCanBeSimplified")
        val EXITED = String("<exited>".toCharArray())
    }
}
