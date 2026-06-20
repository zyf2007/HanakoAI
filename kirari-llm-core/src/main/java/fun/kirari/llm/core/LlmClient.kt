package `fun`.kirari.llm.core

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

class LlmClient(
    private val clientProvider: NetworkClientProvider = NetworkClientProvider(),
    private val logger: LlmLogger = NoopLlmLogger,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val tag = "KirariLlmClient"

    suspend fun stream(request: StreamRequest): Flow<LlmEvent> {
        logger.i(
            tag,
            "stream provider=${request.provider.kind} model=${request.model} imageCount=${request.imagesBase64.size} hasTools=${request.tools != null} trustAllHttps=${request.trustAllHttpsCertificates}"
        )
        val sseClient = SseStreamClient(clientProvider.client(request.trustAllHttpsCertificates), logger)
        val adapter = when (request.provider.kind) {
            ProviderKind.OPENAI_COMPATIBLE,
            ProviderKind.KIRARI_NETWORK -> OpenAiChatAdapter(sseClient, json)
            ProviderKind.OPENAI_RESPONSES -> OpenAiResponsesAdapter(sseClient, json)
            ProviderKind.ANTHROPIC -> AnthropicAdapter(sseClient, json)
            ProviderKind.GOOGLE -> GoogleAdapter(sseClient, json)
        }
        return adapter.stream(request)
    }
}
