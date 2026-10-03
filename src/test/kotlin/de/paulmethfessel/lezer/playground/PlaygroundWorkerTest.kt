package de.paulmethfessel.lezer.playground

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.paulmethfessel.lezer.generator.NodeInstallation
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.io.path.writeText

/** The worker's protocol handling, with shell scripts in place of node, so it doesn't need node or the generator. */
class PlaygroundWorkerTest : BasePlatformTestCase() {
    private lateinit var worker: PlaygroundWorker

    override fun setUp() {
        super.setUp()
        worker = PlaygroundWorker()
        Disposer.register(testRootDisposable, worker)
    }

    /** A worker that exits explains why with its error output. */
    fun testExitMessage() {
        val node = fakeNode("echo 'SyntaxError: something broke' >&2\nexit 1") ?: return
        val result = parse(node, request())
        assertTrue(result.grammarError!!, result.grammarError.contains("SyntaxError: something broke"))
    }

    /** Responses to older requests (that timed out before) are skipped. */
    fun testStaleResponsesAreSkipped() {
        val request = request()
        val node = fakeNode("read line\necho '{\"id\":999,\"grammarError\":\"stale\"}'\necho '{\"id\":${request.id},\"ms\":7}'\ncat > /dev/null") ?: return
        val result = parse(node, request)
        assertNull(result.grammarError)
        assertEquals(7L, result.ms)
    }

    /** A request that wasn't sent yet is dropped when a newer one comes in. */
    fun testReplacedRequestCompletesWithNull() {
        // Keeps the worker busy with the first request for a while
        val node = fakeNode("sleep 1\nexit 1") ?: return
        val first = worker.parse(node, request())
        Thread.sleep(200)
        val replaced = worker.parse(node, request())
        val newest = request()
        val result = worker.parse(node, newest)
        assertNull(replaced.get(30, TimeUnit.SECONDS))
        assertEquals(newest.id, result.get(30, TimeUnit.SECONDS)!!.id)
        assertNotNull(first.get(30, TimeUnit.SECONDS))
    }

    private fun request() = PlaygroundRequest(worker.nextId(), "/missing", "@top P { \"x\" }", "/", "x", null, null, emptyList())

    private fun parse(node: NodeInstallation, request: PlaygroundRequest): PlaygroundResult =
        worker.parse(node, request).get(30, TimeUnit.SECONDS)!!

    /** A shell script with [body] that stands in for node, null on Windows. */
    private fun fakeNode(body: String): NodeInstallation? {
        if (SystemInfo.isWindows) return null
        val script = Files.createTempFile("node", ".sh")
        script.writeText("#!/bin/sh\n$body\n")
        script.toFile().setExecutable(true)
        return NodeInstallation(script)
    }
}
