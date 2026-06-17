package `fun`.kirari.hanako.network

import `fun`.kirari.hanako.data.ModelProviderConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal fun baseRequest(
    provider: ModelProviderConfig,
    url: String,
    payload: JsonObject,
    json: Json,
    mediaType: MediaType,
    headers: Map<String, String> = emptyMap()
): Request {
    val base = provider.baseUrl.trimEnd('/')
    require(base.isNotBlank()) { "请先填写 Base URL" }
    require(provider.apiKey.isNotBlank()) { "请先填写 API Key" }
    val builder = Request.Builder()
        .url(url)
        .header("Content-Type", "application/json")
        .post(json.encodeToString(JsonObject.serializer(), payload).toRequestBody(mediaType))
        .authenticateFor(provider)

    headers.forEach { (key, value) ->
        builder.header(key, value)
    }
    return builder.build()
}

internal fun AiGateway.baseRequest(
    provider: ModelProviderConfig,
    url: String,
    payload: JsonObject,
    headers: Map<String, String> = emptyMap()
): Request = baseRequest(provider, url, payload, json, JSON, headers)
