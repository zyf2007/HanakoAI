package `fun`.kirari.hanako.data

import android.util.Base64
import `fun`.kirari.llm.core.ProviderKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ImportedProviderConfig(
    val kind: ProviderKind,
    val name: String?,
    val baseUrl: String,
    val apiKey: String
)

private val providerImportJson = Json { ignoreUnknownKeys = true }

fun parseImportedProviderConfig(raw: String): ImportedProviderConfig? {
    val text = raw.trim()
    if (text.isBlank()) return null

    parseNewApiChannelConn(text)?.let { return it }
    parseAiProvider(text)?.let { return it }
    return null
}

private fun parseNewApiChannelConn(raw: String): ImportedProviderConfig? {
    if (!raw.contains("newapi_channel_conn")) return null
    val root = runCatching { providerImportJson.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
    val apiKey = root["key"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    val baseUrl = root["url"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    if (apiKey.isBlank() || baseUrl.isBlank()) return null
    return ImportedProviderConfig(
        kind = ProviderKind.OPENAI_COMPATIBLE,
        name = root["name"]?.jsonPrimitive?.contentOrNull?.trim().takeUnless { it.isNullOrBlank() } ?: "NewAPI",
        baseUrl = baseUrl,
        apiKey = apiKey
    )
}

private fun parseAiProvider(raw: String): ImportedProviderConfig? {
    val prefix = "ai-provider:v1:"
    if (!raw.startsWith(prefix)) return null
    val encoded = raw.removePrefix(prefix).trim()
    val decoded = runCatching {
        String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
    }.getOrNull() ?: return null
    val root = runCatching { providerImportJson.parseToJsonElement(decoded).jsonObject }.getOrNull() ?: return null
    val apiKey = root["apiKey"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    val baseUrl = root["baseUrl"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    if (apiKey.isBlank() || baseUrl.isBlank()) return null

    val providerType = root["type"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    val useResponseApi = root["useResponseApi"]?.jsonPrimitive?.contentOrNull == "true"
    return ImportedProviderConfig(
        kind = providerKindFromImportedType(providerType, useResponseApi),
        name = root["name"]?.jsonPrimitive?.contentOrNull?.trim(),
        baseUrl = baseUrl,
        apiKey = apiKey
    )
}

private fun providerKindFromImportedType(type: String, useResponseApi: Boolean): ProviderKind {
    return when (type.lowercase()) {
        "anthropic" -> ProviderKind.ANTHROPIC
        "google", "gemini" -> ProviderKind.GOOGLE
        "openai" -> if (useResponseApi) ProviderKind.OPENAI_RESPONSES else ProviderKind.OPENAI_COMPATIBLE
        else -> if (useResponseApi) ProviderKind.OPENAI_RESPONSES else ProviderKind.OPENAI_COMPATIBLE
    }
}
