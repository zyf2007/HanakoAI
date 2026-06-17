package `fun`.kirari.hanako.network

import `fun`.kirari.hanako.data.ModelProviderConfig
import `fun`.kirari.hanako.data.ProviderKind
import `fun`.kirari.hanako.data.modelsRequestUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request

data class RemoteModelOption(
    val id: String,
    val displayName: String = id
)

data class ConnectionTestResult(
    val success: Boolean,
    val latencyMs: Long = 0,
    val errorMessage: String = ""
)

class ProviderModelsApi(
    private val clientProvider: NetworkClientProvider = NetworkClientProvider(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) : ConnectionTester {
    private companion object {
        const val TIMEOUT_MILLIS = 60_000L
    }
    private fun authenticatedRequestBuilder(provider: ModelProviderConfig): Request.Builder =
        Request.Builder()
            .url(provider.modelsRequestUrl())
            .authenticateFor(provider)

    suspend fun listModels(
        provider: ModelProviderConfig,
        trustAllHttpsCertificates: Boolean = false
    ): List<RemoteModelOption> = withContext(Dispatchers.IO) {
        val request = authenticatedRequestBuilder(provider).get().build()

        clientProvider.client(trustAllHttpsCertificates, TIMEOUT_MILLIS).newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Failed to get models: ${response.code} ${response.body?.string()}")
            }

            val body = response.body?.string().orEmpty()
            return@withContext when (provider.kind) {
                ProviderKind.GOOGLE -> parseGoogleModels(body)
                ProviderKind.OPENAI_COMPATIBLE,
                ProviderKind.OPENAI_RESPONSES,
                ProviderKind.ANTHROPIC -> parseOpenAiLikeModels(body)
            }
        }
    }

    private fun parseOpenAiLikeModels(body: String): List<RemoteModelOption> {
        val root = json.parseToJsonElement(body).jsonObject
        val data = root["data"]?.jsonArray ?: return emptyList()
        return data.mapNotNull { modelJson ->
            val modelObject = modelJson.jsonObject
            val id = modelObject["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val displayName = modelObject["display_name"]?.jsonPrimitive?.contentOrNull
                ?: modelObject["name"]?.jsonPrimitive?.contentOrNull
                ?: id
            RemoteModelOption(id = id, displayName = displayName)
        }
    }

    override suspend fun testConnection(
        provider: ModelProviderConfig,
        trustAllHttpsCertificates: Boolean
    ): ConnectionTestResult = withContext(Dispatchers.IO) {
        if (provider.apiKey.isBlank()) {
            return@withContext ConnectionTestResult(
                success = false,
                errorMessage = "请填写 API 密钥"
            )
        }

        val startTime = System.currentTimeMillis()
        try {
            val request = authenticatedRequestBuilder(provider).get().build()

            clientProvider.client(trustAllHttpsCertificates, TIMEOUT_MILLIS).newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - startTime
                if (!response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val message = parseErrorMessage(body) ?: "HTTP ${response.code}"
                    return@withContext ConnectionTestResult(
                        success = false,
                        latencyMs = latency,
                        errorMessage = message
                    )
                }
                return@withContext ConnectionTestResult(success = true, latencyMs = latency)
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            val message = when {
                e is java.net.UnknownHostException -> "无法解析主机名"
                e is java.net.ConnectException -> "连接被拒绝"
                e is java.net.SocketTimeoutException -> "连接超时"
                e is javax.net.ssl.SSLException -> "SSL 证书错误"
                else -> e.message ?: "未知错误"
            }
            return@withContext ConnectionTestResult(
                success = false,
                latencyMs = latency,
                errorMessage = message
            )
        }
    }

    private fun parseErrorMessage(body: String): String? {
        return runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
        }.getOrNull()
    }

    private fun parseGoogleModels(body: String): List<RemoteModelOption> {
        val root = json.parseToJsonElement(body).jsonObject
        val data = root["models"]?.jsonArray ?: return emptyList()
        return data.mapNotNull { modelJson ->
            val modelObject = modelJson.jsonObject
            val id = modelObject["name"]?.jsonPrimitive?.contentOrNull?.substringAfterLast('/') ?: return@mapNotNull null
            val displayName = modelObject["displayName"]?.jsonPrimitive?.contentOrNull ?: id
            RemoteModelOption(id = id, displayName = displayName)
        }
    }
}
