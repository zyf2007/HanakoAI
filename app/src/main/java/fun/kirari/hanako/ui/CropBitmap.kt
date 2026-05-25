package `fun`.kirari.hanako.ui

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize

fun cropBitmap(
    source: Bitmap,
    start: Offset,
    end: Offset,
    canvasSize: IntSize
): Bitmap {
    if (canvasSize.width == 0 || canvasSize.height == 0) return source
    if (start == end) return source
    val canvasWidth = canvasSize.width.toFloat()
    val canvasHeight = canvasSize.height.toFloat()
    val scale = minOf(canvasWidth / source.width.toFloat(), canvasHeight / source.height.toFloat())
    val displayedWidth = source.width * scale
    val displayedHeight = source.height * scale
    val imageLeft = (canvasWidth - displayedWidth) / 2f
    val imageTop = (canvasHeight - displayedHeight) / 2f
    val imageRight = imageLeft + displayedWidth
    val imageBottom = imageTop + displayedHeight

    val left = minOf(start.x, end.x).coerceIn(imageLeft, imageRight)
    val top = minOf(start.y, end.y).coerceIn(imageTop, imageBottom)
    val right = maxOf(start.x, end.x).coerceIn(imageLeft, imageRight)
    val bottom = maxOf(start.y, end.y).coerceIn(imageTop, imageBottom)

    val cropLeft = ((left - imageLeft) / scale).toInt().coerceIn(0, source.width - 1)
    val cropTop = ((top - imageTop) / scale).toInt().coerceIn(0, source.height - 1)
    val cropWidth = ((right - left) / scale).toInt().coerceAtLeast(1).coerceAtMost(source.width - cropLeft)
    val cropHeight = ((bottom - top) / scale).toInt().coerceAtLeast(1).coerceAtMost(source.height - cropTop)
    return Bitmap.createBitmap(source, cropLeft, cropTop, cropWidth, cropHeight)
}
