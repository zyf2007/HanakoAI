package `fun`.kirari.hanako.capture

import android.graphics.Bitmap
import android.graphics.BitmapFactory

internal interface BinaryScreencap {
    suspend fun capturePng(): ByteArray
}

internal object ShellScreencap {
    suspend fun captureBitmap(source: BinaryScreencap): Bitmap {
        val png = source.capturePng()
        return BitmapFactory.decodeByteArray(png, 0, png.size)
            ?: error("无法解码 screencap 输出的 PNG 数据")
    }
}
