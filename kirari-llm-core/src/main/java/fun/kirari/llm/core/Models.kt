package `fun`.kirari.llm.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
enum class ProviderKind {
    OPENAI_COMPATIBLE,
    OPENAI_RESPONSES,
    ANTHROPIC,
    GOOGLE,
    KIRARI_NETWORK
}

data class ProviderConfig(
    val kind: ProviderKind,
    val baseUrl: String,
    val apiKey: String = "",
    val headers: Map<String, String> = emptyMap()
)

data class ToolParam(
    val name: String,
    val type: String,
    val description: String,
    val pattern: String? = null
)

data class ToolDef(
    val name: String,
    val description: String,
    val params: List<ToolParam>
)

sealed class LlmEvent {
    data class TextDelta(val text: String) : LlmEvent()
    data class ToolCall(val name: String, val arguments: JsonObject) : LlmEvent()
    data object Done : LlmEvent()
}

data class StreamRequest(
    val provider: ProviderConfig,
    val model: String,
    val systemPrompt: String,
    val userPrompt: String,
    val imagesBase64: List<String> = emptyList(),
    val tools: List<ToolDef>? = null,
    val firstDeltaTimeoutMillis: Long,
    val trustAllHttpsCertificates: Boolean
) {
    val hasImages: Boolean get() = imagesBase64.isNotEmpty()
}

internal interface ProviderAdapter {
    suspend fun stream(request: StreamRequest): kotlinx.coroutines.flow.Flow<LlmEvent>
}

internal class PendingToolCall(
    var name: String? = null,
    val arguments: StringBuilder = StringBuilder()
)
