package `fun`.kirari.hanako.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import `fun`.kirari.hanako.automation.BubbleMenuItem
import `fun`.kirari.hanako.debug.AppDebugLogStore
import `fun`.kirari.hanako.ui.theme.HanakoTheme

/**
 * 管理悬浮球扇形菜单的 overlay 窗口。
 *
 * 创建一个全屏 ComposeView overlay，在气泡位置渲染扇形菜单。
 * 遵循 zyf2007 的 Controller 模式：构造函数注入依赖，通过回调与外部通信。
 *
 * @param context           Service 上下文
 * @param windowManager     窗口管理器
 * @param lifecycleOwner    Service（实现 LifecycleOwner）
 * @param viewModelStoreOwner Service（实现 ViewModelStoreOwner）
 * @param savedStateRegistryOwner Service（实现 SavedStateRegistryOwner）
 * @param overlayViewModel  ViewModel，用于收集 UI state
 * @param onItemClick       菜单项点击回调（退场动画开始前触发）
 * @param onDismiss         退场动画完全结束后的回调（用于移除窗口 + 恢复状态）
 */
internal class BubbleMenuController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val lifecycleOwner: LifecycleOwner,
    private val viewModelStoreOwner: ViewModelStoreOwner,
    private val savedStateRegistryOwner: SavedStateRegistryOwner,
    private val overlayViewModel: OverlayViewModel,
    private val onItemClick: (BubbleMenuItem) -> Unit,
    private val onDismiss: () -> Unit
) {
    private val logTag = "HanakoBubbleMenu"
    private var menuView: ComposeView? = null

    fun show() {
        if (menuView != null) return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.TOP
            x = 0
            y = 0
        }
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(viewModelStoreOwner)
            setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)
            setContent {
                HanakoTheme {
                    val uiState by overlayViewModel.uiState.collectAsState()
                    val bubbleState = uiState.bubbleState
                    if (bubbleState is `fun`.kirari.hanako.automation.BubbleState.MenuExpanded) {
                        BubbleMenu(
                            anchorX = bubbleState.anchorX,
                            anchorY = bubbleState.anchorY,
                            settings = uiState.settings,
                            onItemClick = { item -> onItemClick(item) },
                            onDismiss = { },
                            onDismissFinished = { onDismiss() }
                        )
                    }
                }
            }
        }
        menuView = composeView
        runCatching {
            windowManager.addView(composeView, params)
        }.onFailure { error ->
            AppDebugLogStore.e(logTag, "show: failed to add view", error)
            menuView = null
        }
    }

    fun dismiss() {
        menuView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        menuView = null
    }

    fun isVisible(): Boolean = menuView != null
}
