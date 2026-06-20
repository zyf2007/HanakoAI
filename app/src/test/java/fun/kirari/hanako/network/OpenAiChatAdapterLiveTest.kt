package `fun`.kirari.hanako.network

import `fun`.kirari.hanako.data.ModelProviderConfig
import `fun`.kirari.llm.core.LlmEvent
import `fun`.kirari.llm.core.ProviderKind
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiChatAdapterLiveTest {
    @Test
    fun streams_from_openai_compatible_endpoint() = runBlocking {
        val baseUrl = System.getenv("HANAKO_TEST_BASE_URL")
        val apiKey = System.getenv("HANAKO_TEST_API_KEY")
        assumeTrue("Missing HANAKO_TEST_BASE_URL or HANAKO_TEST_API_KEY", !baseUrl.isNullOrBlank() && !apiKey.isNullOrBlank())
        val resolvedBaseUrl = requireNotNull(baseUrl)
        val resolvedApiKey = requireNotNull(apiKey)
        val model = System.getenv("HANAKO_TEST_MODEL")
            ?: "gpt-4o-mini"
        val systemPrompt = System.getenv("HANAKO_TEST_SYSTEM_PROMPT")
            ?: "You are a concise assistant."
        val userPrompt = System.getenv("HANAKO_TEST_USER_PROMPT")
            ?: "Reply with exactly: pong"

        val provider = ModelProviderConfig(
            name = "LiveTest",
            kind = ProviderKind.OPENAI_COMPATIBLE,
            baseUrl = resolvedBaseUrl,
            apiKey = resolvedApiKey,
            chatModel = model,
            visionModel = model,
            ocrModel = model
        )

        val client = UnifiedLLMClient(NetworkClientProvider())

        val events = mutableListOf<LlmEvent>()

        client.stream(
            provider = provider,
            model = model,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            firstDeltaTimeoutMillis = 15_000L
        ).collect { event ->
            println("LIVE_EVENT: $event")
            events += event
        }

        assertTrue("Expected at least one text delta or done event", events.isNotEmpty())
        assertTrue(
            "Expected stream to finish cleanly",
            events.lastOrNull() is LlmEvent.Done
        )
    }
}
