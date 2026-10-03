package de.paulmethfessel.lezer.generator

import com.intellij.openapi.application.PathManager
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

/** Scripts from the plugin's `lezer` resources, copied to files that node can run. */
object NodeScripts {
    @Synchronized
    fun extract(name: String): Path {
        val content = NodeScripts::class.java.getResourceAsStream("/lezer/$name")!!.use { it.readBytes() }
        val file = Path.of(PathManager.getSystemPath(), "lezer", name)
        if (!Files.isRegularFile(file) || !file.readBytes().contentEquals(content)) {
            file.parent.createDirectories()
            file.writeBytes(content)
        }
        return file
    }
}
