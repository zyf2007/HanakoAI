package `fun`.kirari.hanako.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 连接测试状态管理器。
 *
 * 按 providerId 独立管理每个提供方的测试状态，解决切换提供方时状态串页的问题。
 */
class ConnectionTestManager {
    private val _states = MutableStateFlow<Map<String, ConnectionTestState>>(emptyMap())
    val states: StateFlow<Map<String, ConnectionTestState>> = _states.asStateFlow()

    /**
     * 获取指定提供方的测试状态。未测试过时返回 [ConnectionTestState] 默认值。
     */
    fun stateFor(providerId: String): ConnectionTestState {
        return _states.value[providerId] ?: ConnectionTestState()
    }

    /**
     * 设置指定提供方的测试状态。
     */
    fun setState(providerId: String, state: ConnectionTestState) {
        _states.update { current ->
            current + (providerId to state)
        }
    }

    /**
     * 重置指定提供方的测试状态。
     */
    fun reset(providerId: String) {
        _states.update { current ->
            current - providerId
        }
    }

    /**
     * 重置所有提供方的测试状态。
     */
    fun resetAll() {
        _states.value = emptyMap()
    }
}
