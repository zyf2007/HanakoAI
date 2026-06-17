package `fun`.kirari.hanako.network

import `fun`.kirari.hanako.data.ModelProviderConfig
import `fun`.kirari.hanako.data.ProviderKind
import okhttp3.Request

fun Request.Builder.authenticateFor(provider: ModelProviderConfig): Request.Builder = when (provider.kind) {
    ProviderKind.GOOGLE -> header("x-goog-api-key", provider.apiKey)
    ProviderKind.ANTHROPIC -> header("x-api-key", provider.apiKey)
    ProviderKind.OPENAI_COMPATIBLE,
    ProviderKind.OPENAI_RESPONSES -> header("Authorization", "Bearer ${provider.apiKey}")
}
