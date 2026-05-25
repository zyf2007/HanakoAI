package `fun`.kirari.hanako.capture

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import kotlinx.coroutines.suspendCancellableCoroutine
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal object ShizukuShellScreencap : BinaryScreencap {
    override suspend fun capturePng(): ByteArray {
        val service = ShizukuUserServiceClient.acquire()
        return runCatching {
            service.exec(arrayOf("sh", "-c", "screencap -p"))
        }.getOrElse { error ->
            if (error is RemoteException) {
                ShizukuUserServiceClient.invalidate()
            }
            throw error
        }
    }
}

internal object ShizukuUserServiceClient {
    @Volatile
    private var remoteService: IShizukuShellService? = null

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName("fun.kirari.hanako", ShizukuShellService::class.java.name)
    )
        .processNameSuffix("capture")
        .tag("hanako-shizuku-shell")
        .version(1)
        .daemon(false)
        .debuggable(false)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            remoteService = IShizukuShellService.Stub.asInterface(service)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            remoteService = null
        }
    }

    suspend fun acquire(): IShizukuShellService {
        remoteService?.let { return it }
        check(Shizuku.pingBinder()) { "Shizuku 未运行，请先启动 Shizuku 服务。" }

        return suspendCancellableCoroutine { continuation ->
            val callbackConnection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    val remote = IShizukuShellService.Stub.asInterface(service)
                    remoteService = remote
                    if (continuation.isActive) {
                        continuation.resume(remote)
                    }
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    remoteService = null
                }
            }
            runCatching {
                Shizuku.bindUserService(userServiceArgs, callbackConnection)
            }.onFailure { error ->
                if (continuation.isActive) continuation.resumeWithException(error)
            }

            continuation.invokeOnCancellation {
                runCatching {
                    Shizuku.unbindUserService(userServiceArgs, callbackConnection, false)
                }
            }
        }
    }

    fun invalidate() {
        remoteService = null
        runCatching {
            Shizuku.unbindUserService(userServiceArgs, connection, true)
        }
    }
}
