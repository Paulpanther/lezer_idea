package de.paulmethfessel.lezer.playground

import com.google.gson.Gson
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import de.paulmethfessel.lezer.generator.NodeInstallation
import de.paulmethfessel.lezer.generator.NodeScripts
import java.io.BufferedReader
import java.io.BufferedWriter
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * The node process that builds parsers and parses the playground's input, see `lezer/lezer-playground.mjs`. It keeps
 * running, so only the first request pays for starting node and loading the generator.
 */
@Service(Service.Level.PROJECT)
class PlaygroundWorker : Disposable {
    private class Running(val node: NodeInstallation, val process: Process, val writer: BufferedWriter, val reader: BufferedReader)

    private val gson = Gson()
    private val ids = AtomicLong()
    private val executor: ExecutorService = AppExecutorUtil.createBoundedApplicationPoolExecutor("Lezer Playground", 1)
    private val lineReader: ExecutorService = Executors.newCachedThreadPool { Thread(it, "Lezer Playground reader").apply { isDaemon = true } }

    /** The newest request that hasn't been sent yet, older ones are dropped. */
    private val pending = AtomicReference<Pair<PlaygroundRequest, CompletableFuture<PlaygroundResult?>>?>()
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
        val worker = running?.takeIf { it.node == node && it.process.isAlive } ?: start(node)
        worker.writer.write(gson.toJson(request))
        worker.writer.newLine()
        worker.writer.flush()
        while (true) {
            val line = try {
                lineReader.submit<String?> { worker.reader.readLine() }.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            } catch (e: TimeoutException) {
                stop()
                return PlaygroundResult(id = request.id, grammarError = "Parsing took longer than $TIMEOUT_SECONDS seconds")
            } ?: throw IllegalStateException("The worker exited: " + worker.process.errorStream.bufferedReader().readText().take(2000))
            val result = gson.fromJson(line, PlaygroundResult::class.java)
            // Responses to requests that timed out before are skipped
            if (result.id == request.id) return result
        }
    }

    private fun start(node: NodeInstallation): Running {
        stop()
        val process = node.nodeCommandLine(NodeScripts.extract("lezer-playground.mjs").toString()).createProcess()
        lineReader.execute {
            process.errorStream.bufferedReader().forEachLine { LOG.debug("Lezer Playground: $it") }
        }
        return Running(node, process, process.outputStream.bufferedWriter(), process.inputStream.bufferedReader())
            .also { running = it }
    }

    /** Stops the worker, the next request starts a new one that re-imports all modules. */
    fun restart() {
        executor.execute { stop() }
    }

    private fun stop() {
        running?.process?.destroy()
        running = null
    }

    override fun dispose() {
        stop()
        executor.shutdownNow()
        lineReader.shutdownNow()
    }

    companion object {
        private val LOG = logger<PlaygroundWorker>()
        private const val TIMEOUT_SECONDS = 30L

        fun getInstance(project: Project): PlaygroundWorker = project.service()
    }
}
