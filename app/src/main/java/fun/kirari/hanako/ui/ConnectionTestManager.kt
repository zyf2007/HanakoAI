package `fun`.kirari.hanako.ui

import `fun`.kirari.hanako.data.ModelProviderConfig
import `fun`.kirari.hanako.network.ConnectionTestResult
import `fun`.kirari.hanako.network.ConnectionTester
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ConnectionTestManager(
    private val connectionTester: ConnectionTester,
    private val externalScope: CoroutineScope
) {
    private val _states = MutableStateFlow<Map<String, ConnectionTestState>>(emptyMap())
    private val stateFlows = mutableMapOf<String, StateFlow<ConnectionTestState>>()

    fun stateFor(providerId: String): StateFlow<ConnectionTestState> =
        stateFlows.getOrPut(providerId) {
            _states
                .map { it[providerId] ?: ConnectionTestState() }
                .stateIn(
                    scope = externalScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = ConnectionTestState()
                )
        }

    fun test(provider: ModelProviderConfig, trustAllHttpsCertificates: Boolean) {
        val providerId = provider.id
        updateState(providerId, ConnectionTestState(status = ConnectionTestStatus.TESTING))
        externalScope.launch {
            val result = connectionTester.testConnection(provider, trustAllHttpsCertificates)
            updateState(providerId, result.toState())
        }
    }

    fun reset(providerId: String) {
        _states.update { it - providerId }
    }

    private fun updateState(providerId: String, state: ConnectionTestState) {
        _states.update { current -> current + (providerId to state) }
    }

    private fun ConnectionTestResult.toState(): ConnectionTestState =
        if (success) {
            ConnectionTestState(status = ConnectionTestStatus.SUCCESS, latencyMs = latencyMs)
        } else {
            ConnectionTestState(
                status = ConnectionTestStatus.FAILED,
                latencyMs = latencyMs,
                errorMessage = errorMessage
            )
        }
}
