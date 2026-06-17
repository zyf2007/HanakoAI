@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package `fun`.kirari.hanako.ui

import `fun`.kirari.hanako.data.ModelProviderConfig
import `fun`.kirari.hanako.data.ProviderKind
import `fun`.kirari.hanako.network.ConnectionTestResult
import `fun`.kirari.hanako.network.ConnectionTester
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionTestManagerTest {

    @Test
    fun `state is isolated per provider`() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeConnectionTester()
        val manager = ConnectionTestManager(fake, backgroundScope)

        backgroundScope.launch { manager.stateFor("a").collect {} }
        backgroundScope.launch { manager.stateFor("b").collect {} }

        fake.nextResult = ConnectionTestResult(success = true, latencyMs = 123)
        manager.test(providerWith(id = "a"), trustAllHttpsCertificates = false)
        advanceUntilIdle()

        assertEquals(ConnectionTestStatus.SUCCESS, manager.stateFor("a").value.status)
        assertEquals(123, manager.stateFor("a").value.latencyMs)
        assertEquals(ConnectionTestStatus.IDLE, manager.stateFor("b").value.status)
    }

    @Test
    fun `reset clears provider state`() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeConnectionTester()
        val manager = ConnectionTestManager(fake, backgroundScope)

        backgroundScope.launch { manager.stateFor("a").collect {} }

        fake.nextResult = ConnectionTestResult(success = true, latencyMs = 100)
        manager.test(providerWith(id = "a"), trustAllHttpsCertificates = false)
        advanceUntilIdle()
        assertEquals(ConnectionTestStatus.SUCCESS, manager.stateFor("a").value.status)

        manager.reset("a")
        advanceUntilIdle()
        assertEquals(ConnectionTestStatus.IDLE, manager.stateFor("a").value.status)
    }

    @Test
    fun `test passes provider and trustAll to tester`() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeConnectionTester()
        val manager = ConnectionTestManager(fake, backgroundScope)

        backgroundScope.launch { manager.stateFor("a").collect {} }

        val provider = providerWith(id = "a", apiKey = "secret")
        manager.test(provider, trustAllHttpsCertificates = true)
        advanceUntilIdle()

        assertEquals(1, fake.requests.size)
        assertEquals(provider, fake.requests.first().first)
        assertTrue(fake.requests.first().second)
    }

    @Test
    fun `failed result updates state with error message`() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeConnectionTester()
        val manager = ConnectionTestManager(fake, backgroundScope)

        backgroundScope.launch { manager.stateFor("a").collect {} }

        fake.nextResult = ConnectionTestResult(
            success = false,
            errorMessage = "请填写 API 密钥"
        )
        manager.test(providerWith(id = "a"), trustAllHttpsCertificates = false)
        advanceUntilIdle()

        val state = manager.stateFor("a").value
        assertEquals(ConnectionTestStatus.FAILED, state.status)
        assertEquals("请填写 API 密钥", state.errorMessage)
    }

    private fun providerWith(
        id: String,
        kind: ProviderKind = ProviderKind.OPENAI_COMPATIBLE,
        apiKey: String = "key"
    ): ModelProviderConfig = ModelProviderConfig(
        id = id,
        kind = kind,
        baseUrl = "https://example.com",
        apiKey = apiKey
    )

    private class FakeConnectionTester : ConnectionTester {
        val requests = mutableListOf<Pair<ModelProviderConfig, Boolean>>()
        var nextResult: ConnectionTestResult? = null

        override suspend fun testConnection(
            provider: ModelProviderConfig,
            trustAllHttpsCertificates: Boolean
        ): ConnectionTestResult {
            requests += provider to trustAllHttpsCertificates
            return nextResult ?: ConnectionTestResult(success = true)
        }
    }
}
