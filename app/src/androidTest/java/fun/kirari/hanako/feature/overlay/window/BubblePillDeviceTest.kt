package `fun`.kirari.hanako.feature.overlay.window

import android.app.Instrumentation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import `fun`.kirari.hanako.core.data.BubbleAppearanceSettings
import `fun`.kirari.hanako.feature.overlay.state.BubbleState
import `fun`.kirari.hanako.platform.capture.CaptureLaunchMode
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * 悬浮球多选项胶囊的设备端验证。
 *
 * 直接驱动 [BubbleWindowController] 渲染，再从系统截屏的像素上断言
 * 「所有选项都完整落在胶囊内部且没有贴边」——这是纯数学单测覆盖不到的部分。
 *
 * 需要悬浮窗权限，测试会自己用 shell 授权。
 * 截图同时落到 `Android/data/fun.kirari.hanako/files/bubble-captures/` 供人工查看。
 */
@RunWith(AndroidJUnit4::class)
class BubblePillDeviceTest {

    private val pillInteriorColor = Color.parseColor("#E8F0FE")
    private val letterColor = Color.parseColor("#0B57D0")

    /** 字母与胶囊内壁之间至少要留出的余量（px）；贴边即说明被裁切。 */
    private val minimumLetterMarginPx = 6

    private val bubbleCases = listOf("A", "AB", "ABCD", "ABCDEFGH")

    private var controller: BubbleWindowController? = null

    @After
    fun tearDown() {
        destroyBubble()
    }

    @Test
    fun everyOptionStaysInsideTheBubble() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        grantOverlayPermission(instrumentation, context.packageName)
        val windowManager = requireNotNull(context.getSystemService(WindowManager::class.java))
        val outputDirectory = File(context.getExternalFilesDir(null), "bubble-captures").apply { mkdirs() }
        val diagnostics = StringBuilder()
        diagnostics.appendLine("package=${context.packageName}")
        diagnostics.appendLine("canDrawOverlays=${Settings.canDrawOverlays(context)}")
        diagnostics.appendLine("appops=${readAppOps(instrumentation, context.packageName)}")
        diagnostics.appendLine("display=${context.resources.displayMetrics.widthPixels}x" +
            "${context.resources.displayMetrics.heightPixels} density=${context.resources.displayMetrics.density}")

        val failures = mutableListOf<String>()
        for (letters in bubbleCases) {
            val baseline = captureScreen(instrumentation)
            val attempt = runCatching { renderBubble(instrumentation, context, windowManager, letters) }
            val rendered = attempt.getOrNull()
            if (rendered == null) {
                failures += "$letters: 渲染悬浮球失败 -> ${attempt.exceptionOrNull()}"
                destroyBubble()
            } else {
                File(outputDirectory, "$letters.png").outputStream().use {
                    rendered.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                diagnostics.appendLine(verify(letters, baseline, rendered, failures))
                destroyBubble()
            }
        }
        diagnostics.appendLine("failures=${failures.size}")
        failures.forEach { diagnostics.appendLine("  $it") }
        File(outputDirectory, "diagnostics.txt").writeText(diagnostics.toString())

        assertTrue(
            "多选项胶囊渲染不符合预期：\n" + diagnostics,
            failures.isEmpty()
        )
    }

    private fun readAppOps(instrumentation: Instrumentation, packageName: String): String {
        val output = instrumentation.uiAutomation
            .executeShellCommand("appops get $packageName SYSTEM_ALERT_WINDOW")
            .use { parcelFileDescriptor ->
                java.io.FileInputStream(parcelFileDescriptor.fileDescriptor).bufferedReader().readText()
            }
        return output.trim().replace('\n', ' ')
    }

    private fun renderBubble(
        instrumentation: Instrumentation,
        context: Context,
        windowManager: WindowManager,
        letters: String
    ): Bitmap {
        instrumentation.runOnMainSync {
            val bubble = BubbleWindowController(
                context = context,
                windowManager = windowManager,
                onSingleTap = {},
                onDoubleTap = {},
                onLongPress = {},
                onLongPressHaptic = {},
                isStaticModeEnabled = { false },
                bubbleAppearanceSettings = { BubbleAppearanceSettings() }
            )
            controller = bubble
            bubble.show()
            bubble.update(BubbleState.ShowingLetters(letters), CaptureLaunchMode.AUTO)
        }
        instrumentation.waitForIdleSync()
        // 背景色过渡动画 220ms，等它结束再截屏
        SystemClock.sleep(900)
        return captureScreen(instrumentation)
    }

    private fun destroyBubble() {
        val bubble = controller ?: return
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync { bubble.destroy() }
        controller = null
        SystemClock.sleep(250)
    }

    /** 校验并返回本次用例的实测尺寸，写进 diagnostics 便于人工核对。 */
    private fun verify(
        letters: String,
        baseline: Bitmap,
        rendered: Bitmap,
        failures: MutableList<String>
    ): String {
        val changed = changedBounds(baseline, rendered)
        if (changed == null) {
            failures += "$letters: 屏幕上没有检测到悬浮球（截图没有变化）"
            return "$letters: 未检测到悬浮球"
        }
        val pill = colorBounds(rendered, changed, pillInteriorColor, tolerance = 24)
        if (pill == null) {
            failures += "$letters: 没有找到胶囊背景色 #E8F0FE，变化区域=$changed"
            return "$letters: 未找到胶囊，changed=$changed"
        }
        val glyphs = colorBounds(rendered, changed, letterColor, tolerance = 48)
        if (glyphs == null) {
            failures += "$letters: 没有找到字母像素 #0B57D0，变化区域=$changed"
            return "$letters: 未找到字母，changed=$changed"
        }

        val expectedPill = letters.length > 1
        if (expectedPill && pill.width() <= pill.height()) {
            failures += "$letters: 期望比圆形更宽，实际 ${pill.width()}x${pill.height()}"
        }
        if (!expectedPill && pill.width() > pill.height() + 8) {
            failures += "$letters: 单字符应保持圆形，实际 ${pill.width()}x${pill.height()}"
        }
        val leftMargin = glyphs.left - pill.left
        val rightMargin = pill.right - glyphs.right
        if (leftMargin < minimumLetterMarginPx || rightMargin < minimumLetterMarginPx) {
            failures += "$letters: 字母贴边/被裁切 left=$leftMargin right=$rightMargin " +
                "(胶囊 ${pill.width()}px, 文字 ${glyphs.width()}px)"
        }
        return "$letters: changed=$changed pill=${pill.width()}x${pill.height()}@(${pill.left},${pill.top}) " +
            "glyph=${glyphs.width()}x${glyphs.height()} margin L=$leftMargin R=$rightMargin"
    }

    /** 两次截屏之间发生变化的像素范围，用来在任意桌面背景上定位悬浮球。 */
    private fun changedBounds(baseline: Bitmap, rendered: Bitmap): Rect? {
        val width = minOf(baseline.width, rendered.width)
        val height = minOf(baseline.height, rendered.height)
        val baselinePixels = baseline.toPixelArray(width, height)
        val renderedPixels = rendered.toPixelArray(width, height)
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = -1
        var maxY = -1
        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                if (differs(baselinePixels[rowOffset + x], renderedPixels[rowOffset + x])) {
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                }
            }
        }
        return if (maxX < 0) null else Rect(minX, minY, maxX, maxY)
    }

    private fun Bitmap.toPixelArray(width: Int, height: Int): IntArray {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        return pixels
    }

    private fun differs(first: Int, second: Int): Boolean {
        return abs(Color.red(first) - Color.red(second)) > 10 ||
            abs(Color.green(first) - Color.green(second)) > 10 ||
            abs(Color.blue(first) - Color.blue(second)) > 10
    }

    private fun colorBounds(bitmap: Bitmap, area: Rect, target: Int, tolerance: Int): Rect? {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = bitmap.toPixelArray(width, height)
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = -1
        var maxY = -1
        val left = area.left.coerceAtLeast(0)
        val top = area.top.coerceAtLeast(0)
        val right = area.right.coerceAtMost(width - 1)
        val bottom = area.bottom.coerceAtMost(height - 1)
        for (y in top..bottom) {
            val rowOffset = y * width
            for (x in left..right) {
                if (isClose(pixels[rowOffset + x], target, tolerance)) {
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                }
            }
        }
        return if (maxX < 0) null else Rect(minX, minY, maxX, maxY)
    }

    private fun isClose(pixel: Int, target: Int, tolerance: Int): Boolean {
        return abs(Color.red(pixel) - Color.red(target)) <= tolerance &&
            abs(Color.green(pixel) - Color.green(target)) <= tolerance &&
            abs(Color.blue(pixel) - Color.blue(target)) <= tolerance
    }

    private fun captureScreen(instrumentation: Instrumentation): Bitmap {
        return requireNotNull(instrumentation.uiAutomation.takeScreenshot()) {
            "系统截屏失败（屏幕是否已关闭？）"
        }
    }

    private fun grantOverlayPermission(instrumentation: Instrumentation, packageName: String) {
        instrumentation.uiAutomation
            .executeShellCommand("appops set $packageName SYSTEM_ALERT_WINDOW allow")
            .close()
    }
}
