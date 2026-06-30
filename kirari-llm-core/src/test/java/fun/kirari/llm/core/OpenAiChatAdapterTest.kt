package `fun`.kirari.llm.core

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenAiChatAdapterTest {
    @Test
    fun stream_preservesWhitespaceOnlyDeltas() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    """
                    data: {"choices":[{"delta":{"role":"assistant"},"index":0}]}

                    data: {"choices":[{"delta":{"content":"题目内容："},"index":0}]}

                    data: {"choices":[{"delta":{"content":"\n\n"},"index":0}]}

                    data: {"choices":[{"delta":{"content":"-"},"index":0}]}

                    data: {"choices":[{"delta":{"content":" "},"index":0}]}

                    data: {"choices":[{"delta":{"content":"答案"},"index":0}]}

                    data: [DONE]

                    """.trimIndent()
                )
        )
        server.start()
        try {
            val client = LlmClient()
            val events = client.stream(
                StreamRequest(
                    provider = ProviderConfig(
                        kind = ProviderKind.OPENAI_COMPATIBLE,
                        baseUrl = server.url("/v1").toString(),
                        apiKey = "test-key"
                    ),
                    model = "test-model",
                    systemPrompt = "system",
                    userPrompt = "user",
                    firstDeltaTimeoutMillis = 1_000L,
                    trustAllHttpsCertificates = false
                )
            ).toList()

            val text = events.filterIsInstance<LlmEvent.TextDelta>().joinToString(separator = "") { it.text }
            assertEquals("题目内容：\n\n- 答案", text)
        } finally {
            server.shutdown()
        }
    }
}
