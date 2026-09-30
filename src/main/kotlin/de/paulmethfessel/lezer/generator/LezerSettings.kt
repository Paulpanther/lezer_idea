package de.paulmethfessel.lezer.generator

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/** Which `@lezer/generator` installation to use. */
enum class GeneratorMode(val displayName: String) {
    AUTO("Automatic (local, then global)"),
    LOCAL("Local (node_modules)"),
    GLOBAL("Global (npm -g)"),
    CUSTOM("Custom package directory"),
}

@Service(Service.Level.PROJECT)
@State(name = "LezerSettings", storages = [Storage("lezer.xml")])
class LezerSettings : SimplePersistentStateComponent<LezerSettings.State>(State()) {
    class State : BaseState() {
        /** Path of the node executable, detected automatically if blank. */
        var nodePath by string()
        var generator by enum(GeneratorMode.AUTO)
        /** Directory of the `@lezer/generator` package for [GeneratorMode.CUSTOM]. */
        var customGeneratorPath by string()
        var liveErrors by property(true)
        var bannerDismissed by property(false)
    }

    companion object {
        fun getInstance(project: Project): LezerSettings = project.service()
    }
}
