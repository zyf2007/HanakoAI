package `fun`.kirari.llm.core

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
    val displayName: String = id,
    val pricePerTokenCredits: Double? = null,
    val tag: String? = null
)

data class ConnectionTestResult(
    val success: Boolean,
    val latencyMs: Long = 0,
    val errorMessage: String = ""
)

data class ProviderUsageSummary(
    val dailyCredits: Double? = null,
    val dailyCreditsUsed: Double? = null,
    val dailyCreditsLeft: Double? = null,
    val requestsPerMinute: Int? = null,
    val maxInputTokens: Int? = null,
    val maxOutputTokens: Int? = null,
    val groupName: String? = null
)

data class ProviderCatalog(
    val models: List<RemoteModelOption>,
    val usageSummary: ProviderUsageSummary? = null
)

class ProviderModelsApi(
    private val clientProvider: NetworkClientProvider = NetworkClientProvider(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    suspend fun getCatalog(
        provider: ProviderConfig,
        trustAllHttpsCertificates: Boolean = false
    ): ProviderCatalog = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(provider.modelsRequestUrl())
            .applyProviderHeaders(provider)
            .get()
            .build()

        clientProvider.client(trustAllHttpsCertificates).newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Failed to get models: ${response.code} ${response.body?.string()}")
            }

            val body = response.body?.string().orEmpty()
            return@withContext when (provider.kind) {
                ProviderKind.GOOGLE -> ProviderCatalog(models = parseGoogleModels(body))
                ProviderKind.KIRARI_NETWORK -> parseKirariCatalog(body)
                ProviderKind.OPENAI_COMPATIBLE,
                ProviderKind.OPENAI_RESPONSES,
                ProviderKind.ANTHROPIC -> ProviderCatalog(models = parseOpenAiLikeModels(body))
            }
        }
    }

    suspend fun listModels(
        provider: ProviderConfig,
        trustAllHttpsCertificates: Boolean = false
    ): List<RemoteModelOption> = getCatalog(provider, trustAllHttpsCertificates).models

    suspend fun testConnection(
        provider: ProviderConfig,
        trustAllHttpsCertificates: Boolean = false
    ): ConnectionTestResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val request = Request.Builder()
                .url(provider.modelsRequestUrl())
                .applyProviderHeaders(provider)
                .get()
                .build()

            clientProvider.client(trustAllHttpsCertificates).newCall(request).execute().use { response ->
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

    private fun ProviderConfig.modelsRequestUrl(): String {
        val suffix = when (kind) {
            ProviderKind.OPENAI_COMPATIBLE -> "/models"
            ProviderKind.OPENAI_RESPONSES -> "/models"
            ProviderKind.ANTHROPIC -> "/models"
            ProviderKind.GOOGLE -> "/models?pageSize=100"
            ProviderKind.KIRARI_NETWORK -> "/meta"
        }
        return "${baseUrl.trimEnd('/')}$suffix"
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

    private fun parseKirariCatalog(body: String): ProviderCatalog {
        val root = json.parseToJsonElement(body).jsonObject
        val models = root["models"]?.jsonArray ?: return ProviderCatalog(models = emptyList())
        val usageSummary = ProviderUsageSummary(
            dailyCredits = root["daily_credits"]?.jsonPrimitive?.doubleOrNull,
            dailyCreditsUsed = root["daily_credits_used"]?.jsonPrimitive?.doubleOrNull,
            dailyCreditsLeft = root["daily_credits_left"]?.jsonPrimitive?.doubleOrNull,
            requestsPerMinute = root["requests_per_minute"]?.jsonPrimitive?.intOrNull,
            maxInputTokens = root["max_input_tokens"]?.jsonPrimitive?.intOrNull,
            maxOutputTokens = root["max_output_tokens"]?.jsonPrimitive?.intOrNull,
            groupName = root["group_name"]?.jsonPrimitive?.contentOrNull
        )
        return ProviderCatalog(
            models = models.mapNotNull { element ->
                val modelObject = element.jsonObject
                val id = modelObject["name"]?.jsonPrimitive?.contentOrNull
                    ?: modelObject["model_ref"]?.jsonPrimitive?.contentOrNull
                    ?: modelObject["id"]?.jsonPrimitive?.contentOrNull
                    ?: return@mapNotNull null
                val display = modelObject["display_name"]?.jsonPrimitive?.contentOrNull
                    ?: modelObject["name"]?.jsonPrimitive?.contentOrNull
                    ?: modelObject["model_name"]?.jsonPrimitive?.contentOrNull
                    ?: id
                RemoteModelOption(
                    id = id,
                    displayName = display,
                    pricePerTokenCredits = modelObject["price_per_token_credits"]?.jsonPrimitive?.doubleOrNull,
                    tag = modelObject["model_tag"]?.jsonPrimitive?.contentOrNull
                )
            },
            usageSummary = usageSummary
        )
    }

    private fun parseKirariModels(body: String): List<RemoteModelOption> {
        return parseKirariCatalog(body).models
    }

    private val kotlinx.serialization.json.JsonPrimitive.intOrNull: Int?
        get() = contentOrNull?.toIntOrNull()

    private val kotlinx.serialization.json.JsonPrimitive.doubleOrNull: Double?
        get() = contentOrNull?.toDoubleOrNull()

    private fun parseErrorMessage(body: String): String? {
        return runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
        }.getOrNull()
    }
}
