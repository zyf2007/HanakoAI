package `fun`.kirari.llm.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request.Builder

internal fun baseRequest(
    provider: ProviderConfig,
    url: String,
    payload: JsonObject,
    json: Json,
    mediaType: MediaType,
    headers: Map<String, String> = emptyMap()
): Request {
    val base = provider.baseUrl.trimEnd('/')
    require(base.isNotBlank()) { "请先填写 Base URL" }
    val builder = Request.Builder()
        .url(url)
        .header("Content-Type", "application/json")
        .post(json.encodeToString(JsonObject.serializer(), payload).toRequestBody(mediaType))

    builder.applyProviderHeaders(provider, headers)
    return builder.build()
}

internal fun Builder.applyProviderHeaders(
    provider: ProviderConfig,
    extraHeaders: Map<String, String> = emptyMap()
): Builder {
    when (provider.kind) {
        ProviderKind.GOOGLE -> {
            require(provider.apiKey.isNotBlank()) { "请先填写 API Key" }
            header("x-goog-api-key", provider.apiKey)
        }

        ProviderKind.ANTHROPIC -> {
            require(provider.apiKey.isNotBlank()) { "请先填写 API Key" }
            header("x-api-key", provider.apiKey)
        }

        else -> {
            if (provider.apiKey.isNotBlank()) {
                header("Authorization", "Bearer ${provider.apiKey}")
            }
        }
    }

    provider.headers.forEach { (key, value) ->
        header(key, value)
    }
    extraHeaders.forEach { (key, value) ->
        header(key, value)
    }
    return this
}
