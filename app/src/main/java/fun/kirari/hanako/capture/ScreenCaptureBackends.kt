package `fun`.kirari.hanako.capture

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import `fun`.kirari.hanako.data.ScreenCaptureMethod
import `fun`.kirari.hanako.overlay.OverlayLaunchMode
import kotlinx.coroutines.flow.StateFlow
import rikka.shizuku.Shizuku

internal sealed interface ScreenCaptureStartResult {
    data object Started : ScreenCaptureStartResult
    data class UserActionRequired(val message: String) : ScreenCaptureStartResult
    data class Failed(val message: String) : ScreenCaptureStartResult
}

internal interface ScreenCaptureBackend {
    val method: ScreenCaptureMethod
    val sessionActive: StateFlow<Boolean>

    fun hasActiveSession(): Boolean

    fun requestStart(context: Context, launchMode: OverlayLaunchMode): ScreenCaptureStartResult

    suspend fun captureLatestBitmap(context: Context): Bitmap

    fun stop(context: Context)
}

internal object ScreenCaptureManager {
    private val backends: Map<ScreenCaptureMethod, ScreenCaptureBackend> = listOf(
        MediaProjectionCaptureBackend,
        ShizukuCaptureBackend
    ).associateBy(ScreenCaptureBackend::method)

    fun sessionActiveFlow(method: ScreenCaptureMethod): StateFlow<Boolean> = backend(method).sessionActive

    fun hasActiveSession(method: ScreenCaptureMethod): Boolean = backend(method).hasActiveSession()

    fun requestStart(
        context: Context,
        method: ScreenCaptureMethod,
        launchMode: OverlayLaunchMode
    ): ScreenCaptureStartResult = backend(method).requestStart(context, launchMode)

    suspend fun captureLatestBitmap(
        context: Context,
        method: ScreenCaptureMethod
    ): Bitmap = backend(method).captureLatestBitmap(context)

    fun stop(context: Context, method: ScreenCaptureMethod) {
        backend(method).stop(context)
    }

    private fun backend(method: ScreenCaptureMethod): ScreenCaptureBackend {
        return backends.getValue(method)
    }
}

internal object MediaProjectionCaptureBackend : ScreenCaptureBackend {
    override val method: ScreenCaptureMethod = ScreenCaptureMethod.MEDIA_PROJECTION
    override val sessionActive: StateFlow<Boolean> = ProjectionSessionManager.sessionActive

    override fun hasActiveSession(): Boolean = ProjectionSessionManager.hasActiveSession()

    override fun requestStart(context: Context, launchMode: OverlayLaunchMode): ScreenCaptureStartResult {
        context.startActivity(
            Intent(context, ProjectionPermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(ProjectionPermissionActivity.EXTRA_LAUNCH_MODE, launchMode.name)
            }
        )
        return ScreenCaptureStartResult.Started
    }

    override suspend fun captureLatestBitmap(context: Context): Bitmap {
        return ProjectionSessionManager.captureLatestBitmap()
    }

    override fun stop(context: Context) {
        context.stopService(
            Intent(context, MediaProjectionForegroundService::class.java).apply {
                action = MediaProjectionForegroundService.ACTION_STOP
            }
        )
    }
}

internal object ShizukuCaptureBackend : ScreenCaptureBackend {
    override val method: ScreenCaptureMethod = ScreenCaptureMethod.SHIZUKU_ADB
    override val sessionActive: StateFlow<Boolean> = ShizukuAuthorizationManager.authorized

    override fun hasActiveSession(): Boolean = ShizukuAuthorizationManager.authorized.value

    override fun requestStart(context: Context, launchMode: OverlayLaunchMode): ScreenCaptureStartResult {
        if (!Shizuku.pingBinder()) {
            return ScreenCaptureStartResult.Failed("Shizuku 未运行，请先启动 Shizuku 服务。")
        }
        context.startActivity(
            Intent(context, ShizukuPermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(ProjectionPermissionActivity.EXTRA_LAUNCH_MODE, launchMode.name)
            }
        )
        return ScreenCaptureStartResult.Started
    }

    override suspend fun captureLatestBitmap(context: Context): Bitmap {
        if (!Shizuku.pingBinder()) {
            error("Shizuku 未运行，请先启动 Shizuku 服务。")
        }
        if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            ShizukuAuthorizationManager.setAuthorized(false)
            error("Shizuku 尚未授权，请重新启动悬浮球完成授权。")
        }
        ShizukuAuthorizationManager.setAuthorized(true)
        return ShellScreencap.captureBitmap(ShizukuShellScreencap)
    }

    override fun stop(context: Context) {
        ShizukuUserServiceClient.invalidate()
    }
}
