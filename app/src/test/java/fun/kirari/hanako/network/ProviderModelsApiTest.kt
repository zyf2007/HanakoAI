package `fun`.kirari.hanako.network

import `fun`.kirari.hanako.data.ModelProviderConfig
import `fun`.kirari.hanako.data.ProviderKind
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProviderModelsApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ProviderModelsApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = ProviderModelsApi()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `testConnection uses Bearer header for OpenAI Compatible provider`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val provider = providerOf(ProviderKind.OPENAI_COMPATIBLE)
        val result = api.testConnection(provider)

        assertTrue(result.success)
        val request = server.takeRequest()
        assertEquals("Bearer test-key", request.getHeader("Authorization"))
    }

    @Test
    fun `testConnection uses Bearer header for OpenAI Responses provider`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val provider = providerOf(ProviderKind.OPENAI_RESPONSES)
        val result = api.testConnection(provider)

        assertTrue(result.success)
        val request = server.takeRequest()
        assertEquals("Bearer test-key", request.getHeader("Authorization"))
    }

    @Test
    fun `testConnection uses x-api-key header for Anthropic provider`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val provider = providerOf(ProviderKind.ANTHROPIC)
        val result = api.testConnection(provider)

        assertTrue(result.success)
        val request = server.takeRequest()
        assertEquals("test-key", request.getHeader("x-api-key"))
        assertEquals(null, request.getHeader("Authorization"))
    }

    @Test
    fun `testConnection uses x-goog-api-key header for Google provider`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"models\":[]}"))

        val provider = providerOf(ProviderKind.GOOGLE)
        val result = api.testConnection(provider)

        assertTrue(result.success)
        val request = server.takeRequest()
        assertEquals("test-key", request.getHeader("x-goog-api-key"))
        assertEquals(null, request.getHeader("Authorization"))
    }

    @Test
    fun `testConnection returns parsed error message on failure`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error":{"message":"Invalid API key"}}""")
        )

        val provider = providerOf(ProviderKind.OPENAI_COMPATIBLE)
        val result = api.testConnection(provider)

        assertFalse(result.success)
        assertEquals("Invalid API key", result.errorMessage)
    }

    @Test
    fun `testConnection returns failed without network call when apiKey is blank`() = runBlocking {
        val provider = ModelProviderConfig(
            kind = ProviderKind.OPENAI_COMPATIBLE,
            baseUrl = server.url("/").toString(),
            apiKey = ""
        )

        val result = api.testConnection(provider)

        assertFalse(result.success)
        assertEquals("请填写 API 密钥", result.errorMessage)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `listModels uses correct auth header for Google provider`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"models":[{"name":"models/gemini-pro","displayName":"Gemini Pro"}]}""")
        )

        val provider = providerOf(ProviderKind.GOOGLE)
        val models = api.listModels(provider)

        assertEquals(1, models.size)
        assertEquals("gemini-pro", models.first().id)
        val request = server.takeRequest()
        assertEquals("test-key", request.getHeader("x-goog-api-key"))
    }

    private fun providerOf(kind: ProviderKind): ModelProviderConfig =
        ModelProviderConfig(
            kind = kind,
            baseUrl = server.url("/").toString(),
            apiKey = "test-key"
        )
}
