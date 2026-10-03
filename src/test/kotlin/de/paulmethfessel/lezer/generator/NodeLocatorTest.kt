package de.paulmethfessel.lezer.generator

import com.intellij.openapi.util.SystemInfo
import junit.framework.TestCase
import java.io.File
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.name

class NodeLocatorTest : TestCase() {
    fun testNewestVersionFirst() {
        val dir = Files.createTempDirectory("versions")
        for (name in listOf("v9.0.0", "v18.2.1", "v22.1.0", "v18.10.0", "system")) dir.resolve(name).createDirectories()
        dir.resolve("v99.0.0.txt").createFile()
        // Numeric, not alphabetic order, and only version directories
        assertEquals(listOf("v22.1.0", "v18.10.0", "v18.2.1", "v9.0.0"), NodeLocator.newestVersion(dir).map { it.name })
        assertEquals(emptyList<Any>(), NodeLocator.newestVersion(dir.resolve("missing")))
    }

    fun testCandidateDirectories() {
        val home = Files.createTempDirectory("home")
        home.resolve(".nvm/versions/node/v20.0.0").createDirectories()
        home.resolve(".nvm/versions/node/v22.0.0").createDirectories()
        val dirs = NodeLocator.candidateDirectories(home, { null }, isWindows = false)
        val nvm = dirs.filter { it.startsWith(home.resolve(".nvm")) }
        assertEquals(listOf(home.resolve(".nvm/versions/node/v22.0.0/bin"), home.resolve(".nvm/versions/node/v20.0.0/bin")), nvm)
        assertTrue(home.resolve(".volta/bin") in dirs)
    }

    fun testCandidateDirectoriesOnWindows() {
        val appData = Files.createTempDirectory("appdata")
        appData.resolve("nvm/v20.1.0").createDirectories()
        val env = mapOf("ProgramFiles" to "C:\\Program Files", "APPDATA" to appData.toString())
        val dirs = NodeLocator.candidateDirectories(Files.createTempDirectory("home"), env::get, isWindows = true)
        assertEquals(listOf("nodejs", "v20.1.0"), dirs.map { it.name })
    }

    fun testFindInPath() {
        val empty = Files.createTempDirectory("empty")
        val bin = Files.createTempDirectory("bin")
        val node = bin.resolve("node").createFile().apply { toFile().setExecutable(true) }
        bin.resolve("npm").createFile().toFile().setExecutable(false)
        val path = listOf("", empty.toString(), bin.toString()).joinToString(File.pathSeparator)
        assertEquals(node, NodeLocator.findInPath("node", path))
        if (!SystemInfo.isWindows) assertNull("not executable", NodeLocator.findInPath("npm", path))
        assertNull(NodeLocator.findInPath("node", null))
    }
}
