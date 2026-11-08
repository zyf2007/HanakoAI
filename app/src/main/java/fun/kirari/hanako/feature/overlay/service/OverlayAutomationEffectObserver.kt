package `fun`.kirari.hanako.feature.overlay.service

import android.content.Context
import `fun`.kirari.hanako.core.debug.AppDebugLogStore
import `fun`.kirari.hanako.feature.overlay.presentation.OverlayViewModel
import `fun`.kirari.hanako.platform.clipboard.copyToClipboard
import `fun`.kirari.hanako.solve.application.SolveOperations
import `fun`.kirari.hanako.core.model.AutomationActionRecord
import `fun`.kirari.hanako.core.model.AutomationActionType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal class OverlayAutomationEffectObserver(
    private val context: Context,
    private val scope: CoroutineScope,
    private val viewModel: OverlayViewModel,
    private val solveOperations: SolveOperations,
    private val onNotifyAutomationCompleted: (AutomationActionRecord) -> Unit,
    private val onVibrateLetters: suspend (String, `fun`.kirari.hanako.core.data.AutomationSettings) -> Unit
) {
    private val tag = "HanakoAutomationEffects"
    private var observationJob: Job? = null

    fun start() {
        observationJob?.cancel()
        observationJob = scope.launch {
            solveOperations.pendingAutomationResults.collect { pendingResults ->
                pendingResults.values
                    .sortedBy { it.createdAtMillis }
                    .forEach { result ->
                        currentCoroutineContext().ensureActive()
                        val claimed = withContext(NonCancellable) {
                            solveOperations.claimAutomationAction(result.id)
                        } ?: return@forEach
                        try {
                            currentCoroutineContext().ensureActive()
                            val action = requireNotNull(claimed.automationAction)
                            val settings = viewModel.uiState.value.settings
                            when (action.type) {
                                AutomationActionType.SET_CLIPBOARD -> {
                                    action.text.takeIf(String::isNotBlank)?.let { text ->
                                        copyToClipboard(context, "Hanako Auto Copy", text)
                                    }
                                }
                                AutomationActionType.SHOW_BUBBLE_LETTERS -> {
                                    if (settings.automation.staticModeEnabled && action.text.isNotBlank()) {
                                        onVibrateLetters(action.text, settings.automation)
                                    }
                                }
                            }
                            viewModel.presentAutomationEffect(claimed)
                            if (settings.automation.completionNotificationEnabled) {
                                onNotifyAutomationCompleted(action)
                            }
                            withContext(NonCancellable) {
                                solveOperations.completeAutomationAction(claimed.id)
                            }
                            AppDebugLogStore.i(tag, "effect completed resultId=${claimed.id} type=${action.type}")
                        } catch (error: CancellationException) {
                            withContext(NonCancellable) {
                                solveOperations.abandonAutomationAction(claimed.id)
                            }
                            throw error
                        } catch (error: Throwable) {
                            withContext(NonCancellable) {
                                solveOperations.failAutomationAction(claimed.id)
                            }
                            AppDebugLogStore.e(tag, "effect failed resultId=${claimed.id}", error)
                        }
                    }
            }
        }
    }

    fun stop() {
        observationJob?.cancel()
        observationJob = null
    }
}
