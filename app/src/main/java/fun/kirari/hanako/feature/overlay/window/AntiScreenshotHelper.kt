package `fun`.kirari.hanako.feature.overlay.window

import android.os.Build
import android.util.Log
import android.view.SurfaceControl
import android.view.View
import org.lsposed.hiddenapibypass.HiddenApiBypass

internal object AntiScreenshotHelper {
    private const val TAG = "AntiScreenshot"
    private var initialized = false

    fun initialize() {
        if (initialized) return
        initialized = true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        runCatching {
            HiddenApiBypass.addHiddenApiExemptions("L")
        }.onFailure {
            Log.w(TAG, "HiddenApiBypass initialization failed", it)
        }
    }

    /**
     * 对通过 [WindowManager.addView] 添加的悬浮窗 View 应用防截屏保护。
     *
     * 必须在 [WindowManager.addView] 之后调用。
     * 内部通过 [View.post] 延迟到 View 附着窗口后执行，
     * 通过反射获取 [ViewRootImpl.getSurfaceControl] 并调用
     * [SurfaceControl.Transaction.setSkipScreenshot]。
     *
     * 在 Android 12 (API 31) 及以上版本默认启用。
     */
    fun applyTo(view: View) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        view.post { attemptApply(view) }
    }

    private fun attemptApply(view: View) {
        runCatching {
            val getVri = View::class.java.getDeclaredMethod("getViewRootImpl")
            getVri.isAccessible = true
            val vri = getVri.invoke(view) ?: return

            val getSc = vri.javaClass.getMethod("getSurfaceControl")
            val sc = getSc.invoke(vri) as? SurfaceControl ?: return

            val txClass = Class.forName("android.view.SurfaceControl\$Transaction")
            val tx = txClass.getDeclaredConstructor().newInstance()
            val setSkipMethod = txClass.getMethod(
                "setSkipScreenshot",
                SurfaceControl::class.java,
                java.lang.Boolean.TYPE
            )
            setSkipMethod.invoke(tx, sc, true)
            val applyMethod = txClass.getMethod("apply")
            applyMethod.invoke(tx)
        }.onFailure {
            Log.w(TAG, "Failed to apply anti-screenshot", it)
        }
    }
}
