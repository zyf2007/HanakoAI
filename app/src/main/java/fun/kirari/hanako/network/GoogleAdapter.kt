package `fun`.kirari.hanako.network

import `fun`.kirari.hanako.debug.AppDebugLogStore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType

internal class GoogleAdapter(
    private val sseClient: SseStreamClient,
    private val json: Json
) : ProviderAdapter {
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun stream(request: StreamRequest): Flow<LlmEvent> = callbackFlow {
        val payload = buildJsonObject {
            put("systemInstruction", buildJsonObject {
                put("parts", buildJsonArray {
                    add(buildJsonObject { put("text", request.systemPrompt) })
                })
            })
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", request.userPrompt) })
                        request.imagesBase64.forEach { imageBase64 ->
                            add(buildJsonObject {
                                put("inlineData", buildJsonObject {
                                    put("mimeType", "image/jpeg")
                                    put("data", imageBase64)
                                })
                            })
                        }
                    })
                })
            })
            request.tools?.let { tools ->
                put("tools", buildJsonArray {
                    add(ToolRegistry.formatForProvider(tools, request.provider.kind))
                })
                put("toolConfig", buildJsonObject {
                    put("functionCallingConfig", buildJsonObject {
                        put("mode", "ANY")
                    })
                })
            }
        }

        val url = "${request.provider.baseUrl.trimEnd('/')}/models/${request.model}:streamGenerateContent?alt=sse"
        var toolName: String? = null
        var toolArgs: JsonObject? = null

        sseClient.stream(
            request = baseRequest(request.provider, url, payload, json, mediaType),
            firstDeltaTimeoutMillis = request.firstDeltaTimeoutMillis,
            onEvent = { _, _, _, data ->
                val root = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull()
                    ?: return@stream null
                val candidate = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?: return@stream null
                val parts = candidate["content"]?.jsonObject?.get("parts")?.jsonArray.orEmpty()
                var deltaText = ""
                parts.forEach { part ->
                    val obj = part.jsonObject
                    obj["text"]?.jsonPrimitive?.contentOrNull?.let { deltaText += it }
                    obj["functionCall"]?.jsonObject?.let { call ->
                        toolName = call["name"]?.jsonPrimitive?.contentOrNull
                        toolArgs = call["args"]?.jsonObject
                    }
                }
                if (deltaText.isNotBlank()) {
                    trySend(LlmEvent.TextDelta(deltaText))
                }
                SseStreamClient.StreamEventResult(delta = deltaText.ifBlank { null })
            },
            onDelta = {}
        )

        val name = toolName
        val args = toolArgs
        if (name != null && args != null) {
            trySend(LlmEvent.ToolCall(name, args))
        }
        trySend(LlmEvent.Done)
        close()

        awaitClose()
    }
}
