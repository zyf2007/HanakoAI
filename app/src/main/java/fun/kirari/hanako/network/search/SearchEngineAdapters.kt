package `fun`.kirari.hanako.network.search

import `fun`.kirari.hanako.debug.AppDebugLogStore
import `fun`.kirari.hanako.network.NetworkClientProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

/**
 * 根据 HTTP 状态码生成中文错误描述。
 */
private fun errorCodeToMessage(code: Int): String = when (code) {
    401, 403 -> "API Key 无效或权限不足"
    429 -> "请求频率超限，请稍后再试"
    in 500..599 -> "搜索引擎服务暂时不可用"
    else -> "搜索请求失败 (HTTP $code)"
}

/* ------------------------------------------------------------------ */
/*  Tavily                                                             */
/* ------------------------------------------------------------------ */

internal class TavilySearchAdapter(
    private val clientProvider: NetworkClientProvider,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : SearchEngineAdapter {

    private val tag = "HanakoTavilySearch"

    override suspend fun search(request: SearchRequest): SearchResponse =
        withContext(Dispatchers.IO) {
            val payload = buildJsonObject {
                put("api_key", request.apiKey)
                put("query", request.query)
                put("max_results", request.maxResults)
                put("search_depth", "basic")
            }
            val httpRequest = Request.Builder()
                .url(request.baseUrl.trimEnd('/'))
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            clientProvider.clientWithTimeout(request.trustAllHttps, SEARCH_TIMEOUT_MILLIS)
                .newCall(httpRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        val msg = errorCodeToMessage(response.code)
                        AppDebugLogStore.e(tag, "search failed: ${response.code} $msg")
                        return@withContext SearchResponse(
                            hits = emptyList(),
                            errorCode = response.code,
                            errorMessage = msg
                        )
                    }
                    val body = response.body?.string().orEmpty()
                    SearchResponse(hits = parseResults(body))
                }
        }

    private fun parseResults(body: String): List<SearchHit> {
        if (body.isBlank()) return emptyList()
        return try {
            val root = json.parseToJsonElement(body).jsonObject
            root["results"]?.jsonArray?.mapNotNull { element ->
                val obj = element.jsonObject
                val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val content = obj["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
                SearchHit(title = title, content = content)
            } ?: emptyList()
        } catch (e: Exception) {
            AppDebugLogStore.e(tag, "parse error: ${e.message}")
            emptyList()
        }
    }

    private companion object {
        private const val SEARCH_TIMEOUT_MILLIS = 10_000L
    }
}

/* ------------------------------------------------------------------ */
/*  Brave Search                                                       */
/* ------------------------------------------------------------------ */

internal class BraveSearchAdapter(
    private val clientProvider: NetworkClientProvider,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : SearchEngineAdapter {

    private val tag = "HanakoBraveSearch"

    override suspend fun search(request: SearchRequest): SearchResponse =
        withContext(Dispatchers.IO) {
            val url = request.baseUrl.trimEnd('/').toHttpUrl().newBuilder()
                .addQueryParameter("q", request.query)
                .addQueryParameter("count", request.maxResults.toString())
                .build()

            val httpRequest = Request.Builder()
                .url(url)
                .header("X-Subscription-Token", request.apiKey)
                .header("Accept", "application/json")
                .get()
                .build()

            clientProvider.clientWithTimeout(request.trustAllHttps, SEARCH_TIMEOUT_MILLIS)
                .newCall(httpRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        val msg = errorCodeToMessage(response.code)
                        AppDebugLogStore.e(tag, "search failed: ${response.code} $msg")
                        return@withContext SearchResponse(
                            hits = emptyList(),
                            errorCode = response.code,
                            errorMessage = msg
                        )
                    }
                    val body = response.body?.string().orEmpty()
                    SearchResponse(hits = parseResults(body))
                }
        }

    private fun parseResults(body: String): List<SearchHit> {
        if (body.isBlank()) return emptyList()
        return try {
            val root = json.parseToJsonElement(body).jsonObject
            val web = root["web"]?.jsonObject ?: return emptyList()
            web["results"]?.jsonArray?.mapNotNull { element ->
                val obj = element.jsonObject
                val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val description = obj["description"]?.jsonPrimitive?.contentOrNull.orEmpty()
                SearchHit(title = title, content = description)
            } ?: emptyList()
        } catch (e: Exception) {
            AppDebugLogStore.e(tag, "parse error: ${e.message}")
            emptyList()
        }
    }

    private companion object {
        private const val SEARCH_TIMEOUT_MILLIS = 10_000L
    }
}

/* ------------------------------------------------------------------ */
/*  Serper.dev                                                         */
/* ------------------------------------------------------------------ */

internal class SerperSearchAdapter(
    private val clientProvider: NetworkClientProvider,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : SearchEngineAdapter {

    private val tag = "HanakoSerperSearch"

    override suspend fun search(request: SearchRequest): SearchResponse =
        withContext(Dispatchers.IO) {
            val payload = buildJsonObject {
                put("q", request.query)
                put("num", request.maxResults)
            }
            val httpRequest = Request.Builder()
                .url(request.baseUrl.trimEnd('/'))
                .header("X-API-KEY", request.apiKey)
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            clientProvider.clientWithTimeout(request.trustAllHttps, SEARCH_TIMEOUT_MILLIS)
                .newCall(httpRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        val msg = errorCodeToMessage(response.code)
                        AppDebugLogStore.e(tag, "search failed: ${response.code} $msg")
                        return@withContext SearchResponse(
                            hits = emptyList(),
                            errorCode = response.code,
                            errorMessage = msg
                        )
                    }
                    val body = response.body?.string().orEmpty()
                    SearchResponse(hits = parseResults(body))
                }
        }

    private fun parseResults(body: String): List<SearchHit> {
        if (body.isBlank()) return emptyList()
        return try {
            val root = json.parseToJsonElement(body).jsonObject
            root["organic"]?.jsonArray?.mapNotNull { element ->
                val obj = element.jsonObject
                val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val snippet = obj["snippet"]?.jsonPrimitive?.contentOrNull.orEmpty()
                SearchHit(title = title, content = snippet)
            } ?: emptyList()
        } catch (e: Exception) {
            AppDebugLogStore.e(tag, "parse error: ${e.message}")
            emptyList()
        }
    }

    private companion object {
        private const val SEARCH_TIMEOUT_MILLIS = 10_000L
    }
}

/* ------------------------------------------------------------------ */
/*  Custom (Tavily 兼容格式，复用 TavilySearchAdapter)                 */
/* ------------------------------------------------------------------ */

internal class CustomSearchAdapter(
    clientProvider: NetworkClientProvider,
    json: Json = Json { ignoreUnknownKeys = true }
) : SearchEngineAdapter {

    private val delegate = TavilySearchAdapter(clientProvider, json)

    override suspend fun search(request: SearchRequest): SearchResponse =
        delegate.search(request)
}
