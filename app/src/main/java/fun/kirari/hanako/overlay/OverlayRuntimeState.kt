package `fun`.kirari.hanako.overlay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object OverlayRuntimeState {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    fun setRunning(running: Boolean) {
        _running.value = running
    }
}
