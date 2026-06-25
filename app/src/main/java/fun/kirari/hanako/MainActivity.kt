package `fun`.kirari.hanako

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import `fun`.kirari.hanako.ui.HanakoApp
import `fun`.kirari.hanako.ui.MainViewModel
import `fun`.kirari.hanako.ui.theme.HanakoTheme

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()

    private fun consumeKirariRedirect(intent: android.content.Intent?) {
        val data = intent?.data ?: return
        viewModel.handleKirariRedirect(data)
        intent.setData(null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("HanakoAI", "Uncaught exception in thread ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
        consumeKirariRedirect(intent)
        setContent {
            HanakoTheme {
                HanakoApp(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeKirariRedirect(intent)
    }
}
