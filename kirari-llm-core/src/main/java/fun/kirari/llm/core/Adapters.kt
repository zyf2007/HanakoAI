package `fun`.kirari.llm.core

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType

internal class OpenAiChatAdapter(
    private val sseClient: SseStreamClient,
    private val json: Json
) : ProviderAdapter {
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun stream(request: StreamRequest): Flow<LlmEvent> = callbackFlow {
        val payload = buildJsonObject {
            put("model", request.model)
            put("stream", true)
            put("messages", buildJsonArray {
                add(openAiMessage("system", request.systemPrompt))
                add(buildJsonObject {
                    put("role", "user")
                    if (!request.hasImages) {
                        put("content", request.userPrompt)
                    } else {
                        put("content", buildJsonArray {
                            add(buildJsonObject {
                                put("type", "text")
                                put("text", request.userPrompt)
                            })
                            request.imagesBase64.forEach { imageBase64 ->
                                add(buildJsonObject {
                                    put("type", "image_url")
                                    put("image_url", buildJsonObject {
                                        put("url", "data:image/jpeg;base64,$imageBase64")
                                        put("detail", "high")
                                    })
                                })
                            }
                        })
                    }
                })
            })
            request.tools?.let { put("tools", ToolRegistry.formatForProvider(it, request.provider.kind)) }
        }

        val toolCalls = mutableMapOf<Int, PendingToolCall>()

        sseClient.stream(
            request = baseRequest(request.provider, "${request.provider.baseUrl.trimEnd('/')}/chat/completions", payload, json, mediaType),
            firstDeltaTimeoutMillis = request.firstDeltaTimeoutMillis,
            onEvent = { _, _, _, data ->
                if (data == "[DONE]") {
                    SseStreamClient.StreamEventResult(done = true)
                } else {
                    val root = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull()
                        ?: return@stream null
                    val choice = (root["choices"] as? JsonArray)?.firstOrNull()?.jsonObject
                        ?: return@stream null
                    val delta = choice["delta"]?.jsonObject ?: return@stream null

                    val textDelta = extractOpenAiContent(delta["content"])
                    if (textDelta.isNotBlank()) {
                        trySend(LlmEvent.TextDelta(textDelta))
                    }

                    (delta["tool_calls"] as? JsonArray)?.forEach { item ->
                        val toolCall = item.jsonObject
                        val index = toolCall["index"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                        val function = toolCall["function"]?.jsonObject ?: return@forEach
                        val tc = toolCalls.getOrPut(index) { PendingToolCall() }
                        function["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let {
                            tc.name = it
                        }
                        tc.arguments.append(function["arguments"]?.jsonPrimitive?.contentOrNull.orEmpty())
                    }

                    SseStreamClient.StreamEventResult(delta = textDelta.ifBlank { null })
                }
            },
            onDelta = {}
        )

        for (tc in toolCalls.values) {
            val name = tc.name ?: continue
            val args = runCatching { json.parseToJsonElement(tc.arguments.toString()).jsonObject }.getOrNull()
                ?: continue
            trySend(LlmEvent.ToolCall(name, args))
        }
        trySend(LlmEvent.Done)
        close()

        awaitClose()
    }
}

internal class OpenAiResponsesAdapter(
    private val sseClient: SseStreamClient,
    private val json: Json
) : ProviderAdapter {
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun stream(request: StreamRequest): Flow<LlmEvent> = callbackFlow {
        val payload = buildJsonObject {
            put("model", request.model)
            put("stream", true)
            put("instructions", request.systemPrompt)
            request.tools?.let {
                put("tool_choice", "required")
                put("tools", ToolRegistry.formatForProvider(it, request.provider.kind))
            }
            put("input", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "input_text")
                            put("text", request.userPrompt)
                        })
                        request.imagesBase64.forEach { imageBase64 ->
                            add(buildJsonObject {
                                put("type", "input_image")
                                put("image_url", "data:image/jpeg;base64,$imageBase64")
                            })
                        }
                    })
                })
            })
        }

        val toolCalls = linkedMapOf<String, PendingToolCall>()

        sseClient.stream(
            request = baseRequest(request.provider, "${request.provider.baseUrl.trimEnd('/')}/responses", payload, json, mediaType),
            firstDeltaTimeoutMillis = request.firstDeltaTimeoutMillis,
            onEvent = { _, type, _, data ->
                if (data == "[DONE]") return@stream SseStreamClient.StreamEventResult(done = true)
                val root = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull()
                    ?: return@stream null
                when (type) {
                    "response.output_text.delta" -> {
                        val delta = root["delta"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        if (delta.isNotBlank()) {
                            trySend(LlmEvent.TextDelta(delta))
                        }
                        SseStreamClient.StreamEventResult(delta = delta)
                    }

                    "response.function_call_arguments.delta" -> {
                        val itemId = root["item_id"]?.jsonPrimitive?.contentOrNull ?: return@stream null
                        val tc = toolCalls.getOrPut(itemId) { PendingToolCall() }
                        tc.arguments.append(root["delta"]?.jsonPrimitive?.contentOrNull.orEmpty())
                        null
                    }

                    "response.output_item.added" -> {
                        val item = root["item"]?.jsonObject ?: return@stream null
                        if (item["type"]?.jsonPrimitive?.contentOrNull == "function_call") {
                            val itemId = item["id"]?.jsonPrimitive?.contentOrNull ?: return@stream null
                            val tc = toolCalls.getOrPut(itemId) { PendingToolCall() }
                            tc.name = item["name"]?.jsonPrimitive?.contentOrNull
                            item["arguments"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }?.let {
                                tc.arguments.clear()
                                tc.arguments.append(it)
                            }
                        }
                        null
                    }

                    "response.function_call_arguments.done" -> {
                        val itemId = root["item_id"]?.jsonPrimitive?.contentOrNull ?: return@stream null
                        val tc = toolCalls.getOrPut(itemId) { PendingToolCall() }
                        root["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let {
                            tc.name = it
                        }
                        root["arguments"]?.jsonPrimitive?.contentOrNull?.let {
                            tc.arguments.clear()
                            tc.arguments.append(it)
                        }
                        null
                    }

                    else -> null
                }
            },
            onDelta = {}
        )

        for (tc in toolCalls.values) {
            val name = tc.name ?: continue
            val args = runCatching { json.parseToJsonElement(tc.arguments.toString()).jsonObject }.getOrNull()
                ?: continue
            trySend(LlmEvent.ToolCall(name, args))
        }
        trySend(LlmEvent.Done)
        close()

        awaitClose()
    }
}

internal class AnthropicAdapter(
    private val sseClient: SseStreamClient,
    private val json: Json
) : ProviderAdapter {
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun stream(request: StreamRequest): Flow<LlmEvent> = callbackFlow {
        val payload = buildJsonObject {
            put("model", request.model)
            put("stream", true)
            put("max_tokens", 4096)
            put("system", request.systemPrompt)
            request.tools?.let {
                put("tool_choice", buildJsonObject { put("type", "any") })
                put("tools", ToolRegistry.formatForProvider(it, request.provider.kind))
            }
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", request.userPrompt)
                        })
                        request.imagesBase64.forEach { imageBase64 ->
                            add(buildJsonObject {
                                put("type", "image")
                                put("source", buildJsonObject {
                                    put("type", "base64")
                                    put("media_type", "image/jpeg")
                                    put("data", imageBase64)
                                })
                            })
                        }
                    })
                })
            })
        }

        val toolCallsByIndex = linkedMapOf<Int, PendingToolCall>()

        sseClient.stream(
            request = baseRequest(
                request.provider,
                "${request.provider.baseUrl.trimEnd('/')}/messages",
                payload,
                json,
                mediaType,
                headers = mapOf("anthropic-version" to "2023-06-01")
            ),
            firstDeltaTimeoutMillis = request.firstDeltaTimeoutMillis,
            onEvent = { _, type, _, data ->
                if (data == "[DONE]") return@stream SseStreamClient.StreamEventResult(done = true)
                val root = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull()
                    ?: return@stream null
                when (type) {
                    "content_block_start" -> {
                        val index = root["index"]?.jsonPrimitive?.intOrNull ?: return@stream null
                        val block = root["content_block"]?.jsonObject ?: return@stream null
                        if (block["type"]?.jsonPrimitive?.contentOrNull == "tool_use") {
                            val tc = toolCallsByIndex.getOrPut(index) { PendingToolCall() }
                            tc.name = block["name"]?.jsonPrimitive?.contentOrNull
                            block["input"]?.jsonObject?.takeIf { it.isNotEmpty() }?.let {
                                tc.arguments.clear()
                                tc.arguments.append(json.encodeToString(JsonObject.serializer(), it))
                            }
                        }
                        null
                    }

                    "content_block_delta" -> {
                        val index = root["index"]?.jsonPrimitive?.intOrNull ?: return@stream null
                        val delta = root["delta"]?.jsonObject ?: return@stream null
                        when (delta["type"]?.jsonPrimitive?.contentOrNull) {
                            "text_delta" -> {
                                val text = delta["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                                if (text.isNotBlank()) {
                                    trySend(LlmEvent.TextDelta(text))
                                }
                                SseStreamClient.StreamEventResult(delta = text)
                            }

                            "input_json_delta" -> {
                                val tc = toolCallsByIndex.getOrPut(index) { PendingToolCall() }
                                tc.arguments.append(delta["partial_json"]?.jsonPrimitive?.contentOrNull.orEmpty())
                                null
                            }

                            else -> null
                        }
                    }

                    else -> null
                }
            },
            onDelta = {}
        )

        for (tc in toolCallsByIndex.values) {
            val name = tc.name ?: continue
            val args = runCatching { json.parseToJsonElement(tc.arguments.toString()).jsonObject }.getOrNull()
                ?: continue
            trySend(LlmEvent.ToolCall(name, args))
        }
        trySend(LlmEvent.Done)
        close()

        awaitClose()
    }
}

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
