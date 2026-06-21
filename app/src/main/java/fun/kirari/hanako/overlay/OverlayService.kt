package `fun`.kirari.hanako.overlay

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.provider.Settings
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ServiceLifecycleDispatcher
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import `fun`.kirari.hanako.capture.ProjectionPermissionActivity
import `fun`.kirari.hanako.debug.AppDebugLogStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class OverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val logTag = "HanakoOverlaySvc"
    private val dispatcher = ServiceLifecycleDispatcher(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val serviceViewModelStore = ViewModelStore()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override val lifecycle: Lifecycle
        get() = dispatcher.lifecycle

    override val savedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override val viewModelStore: ViewModelStore
        get() = serviceViewModelStore

    private lateinit var overlayViewModel: OverlayViewModel
    private var bubbleWindowController: BubbleWindowController? = null
    private var panelWindowController: PanelWindowController? = null
    private var bubbleMenuController: BubbleMenuController? = null
    private var stateObserver: OverlayStateObserver? = null

    override fun onCreate() {
        dispatcher.onServicePreSuperOnCreate()
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        runCatching {
            overlayViewModel = ViewModelProvider(
                this,
                OverlayViewModel.factory(applicationContext)
            )[OverlayViewModel::class.java]
            bubbleWindowController = BubbleWindowController(
                context = this,
                windowManager = windowManager,
                onSingleTap = overlayViewModel::handleSingleTap,
                onDoubleTap = {
                    val center = bubbleWindowController?.getBubbleCenter()
                    if (center != null) {
                        overlayViewModel.handleDoubleTap(center.first, center.second)
                    } else {
                        overlayViewModel.handleDoubleTap()
                    }
                    showMenuIfExpanded()
                },
                onLongPress = {
                    val center = bubbleWindowController?.getBubbleCenter()
                    if (center != null) {
                        overlayViewModel.handleLongPress(center.first, center.second)
                    } else {
                        overlayViewModel.handleLongPress()
                    }
                    showMenuIfExpanded()
                },
                onLongPressHaptic = { vibrateShort() },
                isStaticModeEnabled = {
                    overlayViewModel.uiState.value.settings.automation.staticModeEnabled
                }
            )
            bubbleMenuController = BubbleMenuController(
                context = this,
                windowManager = windowManager,
                lifecycleOwner = this,
                viewModelStoreOwner = this,
                savedStateRegistryOwner = this,
                overlayViewModel = overlayViewModel,
                onItemClick = { item ->
                    overlayViewModel.handleMenuSelect(item)
                },
                onDismiss = {
                    overlayViewModel.onMenuDismissed()
                    bubbleMenuController?.dismiss()
                }
            )
            panelWindowController = PanelWindowController(
                context = this,
                windowManager = windowManager,
                scope = serviceScope,
                lifecycleOwner = this,
                viewModelStoreOwner = this,
                savedStateRegistryOwner = this,
                viewModel = overlayViewModel
            )
            val bubbleController = checkNotNull(bubbleWindowController)
            val panelController = checkNotNull(panelWindowController)
            stateObserver = OverlayStateObserver(
                context = this,
                scope = serviceScope,
                viewModel = overlayViewModel,
                bubbleWindowController = bubbleController,
                panelWindowController = panelController,
                onNotifyAutomationCompleted = ::notifyAutomationCompleted,
                onVibrateLetters = { letters, settings -> vibrateLetters(letters, settings) }
            )
            OverlayRuntimeState.setRunning(true)
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, buildNotification())
            bubbleController.show()
            stateObserver?.start()
        }.onFailure {
            AppDebugLogStore.e(logTag, "Overlay initialization failed", it)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        dispatcher.onServicePreSuperOnStart()
        when (intent?.action) {
            ACTION_STOP -> {
                panelWindowController?.dismiss()
                stopSelf()
            }
            else -> {
                intent?.getStringExtra(ProjectionPermissionActivity.EXTRA_LAUNCH_MODE)
                    ?.let { runCatching { OverlayLaunchMode.valueOf(it) }.getOrNull() }
                    ?.let(overlayViewModel::setLaunchMode)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        bubbleMenuController?.dismiss()
        bubbleMenuController = null
        panelWindowController?.dismiss()
        panelWindowController = null
        bubbleWindowController?.destroy()
        bubbleWindowController = null
        stateObserver?.stop()
        stateObserver = null
        OverlayRuntimeState.setRunning(false)
        serviceViewModelStore.clear()
        serviceScope.cancel()
        super.onDestroy()
        dispatcher.onServicePreSuperOnDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showMenuIfExpanded() {
        val state = overlayViewModel.bubbleStateMachine.currentState
        if (state is `fun`.kirari.hanako.automation.BubbleState.MenuExpanded) {
            bubbleMenuController?.show()
        } else {
            bubbleMenuController?.dismiss()
        }
    }

    companion object {
        const val ACTION_STOP = "fun.kirari.hanako.overlay.STOP"
        internal const val CHANNEL_ID = "overlay_service"
        internal const val AUTOMATION_CHANNEL_ID = "overlay_automation"
        private const val NOTIFICATION_ID = 1001
        internal const val AUTOMATION_COMPLETE_NOTIFICATION_ID = 1002
    }
}
