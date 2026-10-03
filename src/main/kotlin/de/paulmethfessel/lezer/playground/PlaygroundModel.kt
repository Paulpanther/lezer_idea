package de.paulmethfessel.lezer.playground

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/** An `@external` declaration of the grammar, whose implementation the worker imports from [source]. */
data class ExternalDeclaration(val kind: String, val name: String, val source: String)

/** A request to the worker, see `lezer/lezer-playground.mjs`. */
data class PlaygroundRequest(
    val id: Long,
    /** The generator's ES module, see [de.paulmethfessel.lezer.generator.LezerGenerator.module]. */
    val generatorModule: String,
    val grammar: String,
    val grammarDir: String,
    val input: String,
    val top: String?,
    val dialect: String?,
    val externals: List<ExternalDeclaration>,
)

/** A node of the parse tree, with the worker's short property names. */
class PlaygroundNode(
    @SerializedName("n") val name: String = "",
    @SerializedName("f") val from: Int = 0,
    @SerializedName("t") val to: Int = 0,
    @SerializedName("e") val isError: Boolean = false,
    @SerializedName("c") val children: List<PlaygroundNode>? = null,
) {
    val childList: List<PlaygroundNode> get() = children.orEmpty()

    /** The innermost node that contains [offset], preferring the node that starts at it over the one that ends there. */
    fun deepestAt(offset: Int): List<PlaygroundNode> {
        if (offset < from || offset > to) return emptyList()
        val child = childList.firstOrNull { offset >= it.from && offset < it.to }
            ?: childList.lastOrNull { offset >= it.from && offset <= it.to }
        return listOf(this) + (child?.deepestAt(offset) ?: emptyList())
    }

    fun walk(): Sequence<PlaygroundNode> = sequenceOf(this) + childList.asSequence().flatMap { it.walk() }

    override fun toString(): String = name
}

class PlaygroundResult(
    val id: Long = 0,
    val tree: PlaygroundNode? = null,
    val grammarError: String? = null,
    val moduleErrors: List<String> = emptyList(),
    /** The files of all `@external` implementations the worker imported so far. */
    val modules: List<String> = emptyList(),
    val truncated: Boolean = false,
    val ms: Long = 0,
) {
    /** The worker's JSON. Gson doesn't call constructors, so missing properties are null even if Kotlin says otherwise. */
    private class Json(
        val id: Long?, val tree: PlaygroundNode?, val grammarError: String?, val moduleErrors: List<String>?,
        val modules: List<String>?, val truncated: Boolean?, val ms: Long?,
    )

    companion object {
        fun parse(gson: Gson, json: String): PlaygroundResult {
            val result = gson.fromJson(json, Json::class.java) ?: Json(null, null, null, null, null, null, null)
            return PlaygroundResult(
                result.id ?: 0, result.tree, result.grammarError, result.moduleErrors.orEmpty(), result.modules.orEmpty(),
                result.truncated ?: false, result.ms ?: 0,
            )
        }
    }
}
