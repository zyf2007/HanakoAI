package `fun`.kirari.hanako.capture

import java.io.ByteArrayOutputStream
import java.io.InputStream

class ShizukuShellService : IShizukuShellService.Stub() {
    override fun exec(command: Array<out String>?): ByteArray {
        val safeCommand = command?.toList().orEmpty()
        require(safeCommand.isNotEmpty()) { "command must not be empty" }

        val process = ProcessBuilder(safeCommand)
            .redirectErrorStream(false)
            .start()
        val stdout = process.inputStream.use(::readAllBytesCompat)
        val stderr = process.errorStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            error(stderr.ifBlank { "命令执行失败，退出码=$exitCode" })
        }
        return stdout
    }
}

private fun readAllBytesCompat(inputStream: InputStream): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val count = inputStream.read(buffer)
        if (count <= 0) break
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
