package de.paulmethfessel.lezer.playground

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/** The playground's input text for each grammar, stored in the workspace since it is personal scratch text. */
@Service(Service.Level.PROJECT)
@State(name = "LezerPlayground", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class LezerPlaygroundState : SimplePersistentStateComponent<LezerPlaygroundState.State>(State()) {
    class State : BaseState() {
        var inputs by map<String, String>()
        var tops by map<String, String>()
        var dialects by map<String, String>()
    }

    fun input(grammarPath: String): String = state.inputs[grammarPath].orEmpty()

    fun setInput(grammarPath: String, text: String) {
        if (state.inputs[grammarPath] == text) return
        state.inputs[grammarPath] = text
    }

    fun top(grammarPath: String): String? = state.tops[grammarPath]

    fun setTop(grammarPath: String, top: String?) {
        if (top == null) state.tops.remove(grammarPath) else state.tops[grammarPath] = top
    }

    fun dialects(grammarPath: String): Set<String> = state.dialects[grammarPath]?.split(' ')?.filter { it.isNotEmpty() }?.toSet().orEmpty()

    fun setDialects(grammarPath: String, dialects: Set<String>) {
        state.dialects[grammarPath] = dialects.joinToString(" ")
    }

    companion object {
        fun getInstance(project: Project): LezerPlaygroundState = project.service()
    }
}
