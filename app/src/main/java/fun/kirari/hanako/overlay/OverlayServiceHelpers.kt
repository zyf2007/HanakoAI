package `fun`.kirari.hanako.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.app.Service
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationCompat
import `fun`.kirari.hanako.MainActivity
import `fun`.kirari.hanako.R
import `fun`.kirari.hanako.data.AutomationSettings
import `fun`.kirari.hanako.debug.AppDebugLogStore
import kotlinx.coroutines.delay

internal fun OverlayService.buildNotification(): Notification {
    val openIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    return NotificationCompat.Builder(this, OverlayService.CHANNEL_ID)
        .setContentTitle(getString(R.string.overlay_notification_title))
        .setContentText(getString(R.string.overlay_notification_text))
        .setSmallIcon(R.mipmap.ic_launcher_round)
        .setContentIntent(openIntent)
        .setOngoing(true)
        .build()
}

internal fun OverlayService.notifyAutomationCompleted(label: String?) {
    val content = label?.take(32)?.let { "已复制：$it" } ?: "自动模式已完成并复制到剪贴板"
    val notification = NotificationCompat.Builder(this, OverlayService.AUTOMATION_CHANNEL_ID)
        .setContentTitle(getString(R.string.overlay_automation_done_title))
        .setContentText(content)
        .setSmallIcon(R.mipmap.ic_launcher_round)
        .setAutoCancel(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                1,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()
    NotificationManagerCompat.from(this).notify(
        OverlayService.AUTOMATION_COMPLETE_NOTIFICATION_ID,
        notification
    )
}

internal fun OverlayService.openMainActivity() {
    openMainActivity(this)
}

internal fun openMainActivity(context: android.content.Context) {
    context.startActivity(
        Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
    )
}

internal fun OverlayService.vibrateShort() {
    vibrateShort(30L)
}

internal fun OverlayService.vibrateShort(durationMs: Long) {
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(VibratorManager::class.java)
            val vibrator = vibratorManager?.defaultVibrator
            if (vibrator == null || !vibrator.hasVibrator()) {
                AppDebugLogStore.i("HanakoOverlayService", "vibrateShort skipped: no vibrator on device")
                return
            }
            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    durationMs,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
        } else {
            @Suppress("DEPRECATION")
            val vibrator = getSystemService(Service.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator == null || !vibrator.hasVibrator()) {
                AppDebugLogStore.i("HanakoOverlayService", "vibrateShort skipped: no vibrator on device")
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(
                        durationMs,
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        }
        AppDebugLogStore.d("HanakoOverlayService", "vibrateShort triggered")
    }.onFailure { error ->
        AppDebugLogStore.e("HanakoOverlayService", "vibrateShort failed", error)
    }
}

internal suspend fun OverlayService.vibrateLetters(
    text: String,
    settings: AutomationSettings
) {
    AppDebugLogStore.i("HanakoOverlayService", "vibrateLetters text=$text")
    text.uppercase().forEachIndexed { index, ch ->
        val count = ch.code - 'A'.code + 1
        if (count <= 0) return@forEachIndexed
        repeat(count) { pulseIndex ->
            vibrateShort()
            if (pulseIndex != count - 1) {
                delay(settings.staticIntraLetterGapMs.toLong())
            }
        }
        if (index != text.lastIndex) {
            delay(settings.staticInterLetterGapMs.toLong())
        }
    }
}

internal fun OverlayService.createNotificationChannel() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val overlayChannel = NotificationChannel(
        OverlayService.CHANNEL_ID,
        "Hanako Overlay",
        NotificationManager.IMPORTANCE_LOW
    )
    val automationChannel = NotificationChannel(
        OverlayService.AUTOMATION_CHANNEL_ID,
        "Hanako Automation",
        NotificationManager.IMPORTANCE_DEFAULT
    )
    getSystemService(NotificationManager::class.java).apply {
        createNotificationChannel(overlayChannel)
        createNotificationChannel(automationChannel)
    }
}
