package `fun`.kirari.hanako.capture

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import `fun`.kirari.hanako.overlay.OverlayLaunchMode
import `fun`.kirari.hanako.overlay.OverlayService
import rikka.shizuku.Shizuku

class ShizukuPermissionActivity : ComponentActivity() {
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode != REQUEST_CODE) return@OnRequestPermissionResultListener
        val granted = grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED
        ShizukuAuthorizationManager.setAuthorized(granted)
        if (granted) {
            startService(
                Intent(this, OverlayService::class.java).apply {
                    putExtra(ProjectionPermissionActivity.EXTRA_LAUNCH_MODE, launchMode.name)
                }
            )
            setResult(Activity.RESULT_OK)
        } else {
            setResult(Activity.RESULT_CANCELED)
        }
        finish()
    }

    private lateinit var launchMode: OverlayLaunchMode

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        launchMode = intent.getStringExtra(ProjectionPermissionActivity.EXTRA_LAUNCH_MODE)
            ?.let { runCatching { OverlayLaunchMode.valueOf(it) }.getOrNull() }
            ?: OverlayLaunchMode.NORMAL

        if (!Shizuku.pingBinder()) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        when {
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED -> {
                ShizukuAuthorizationManager.setAuthorized(true)
                startService(
                    Intent(this, OverlayService::class.java).apply {
                        putExtra(ProjectionPermissionActivity.EXTRA_LAUNCH_MODE, launchMode.name)
                    }
                )
                setResult(Activity.RESULT_OK)
                finish()
            }

            Shizuku.shouldShowRequestPermissionRationale() -> {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }

            else -> {
                Shizuku.addRequestPermissionResultListener(permissionListener)
                Shizuku.requestPermission(REQUEST_CODE)
            }
        }
    }

    override fun onDestroy() {
        runCatching { Shizuku.removeRequestPermissionResultListener(permissionListener) }
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_CODE = 1001
    }
}
