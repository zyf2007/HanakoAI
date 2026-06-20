package `fun`.kirari.hanako.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.data.ModelProviderConfig
import `fun`.kirari.hanako.ui.components.ProviderEditor
import `fun`.kirari.hanako.ui.components.SectionCard

@Composable
fun GenericProviderDetailScreen(
    provider: ModelProviderConfig,
    connectionTestState: ConnectionTestState,
    onUpdateProvider: (ModelProviderConfig) -> Unit,
    onViewModels: () -> Unit,
    onTestConnection: (ModelProviderConfig) -> Unit
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionCard(title = "提供方配置") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProviderEditor(
                        provider = provider,
                        readOnly = false,
                        onChange = onUpdateProvider,
                        onImportResult = { message ->
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    )
                    GenericViewModelsActionCard(onClick = onViewModels)
                    GenericConnectionTestActionCard(
                        provider = provider,
                        connectionTestState = connectionTestState,
                        onTestConnection = onTestConnection
                    )
                }
            }
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun GenericViewModelsActionCard(
    onClick: () -> Unit
) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        androidx.compose.material3.Text("查看可用模型")
    }
}

@Composable
private fun GenericConnectionTestActionCard(
    provider: ModelProviderConfig,
    connectionTestState: ConnectionTestState,
    onTestConnection: (ModelProviderConfig) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.OutlinedButton(
            onClick = { onTestConnection(provider) },
            enabled = connectionTestState.status != ConnectionTestStatus.TESTING
        ) {
            if (connectionTestState.status == ConnectionTestStatus.TESTING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.size(8.dp))
                androidx.compose.material3.Text("测试中...")
            } else {
                androidx.compose.material3.Text("测试连接")
            }
        }
        GenericConnectionTestInlineResult(
            state = connectionTestState,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

@Composable
private fun GenericConnectionTestInlineResult(
    state: ConnectionTestState,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = state.status == ConnectionTestStatus.SUCCESS || state.status == ConnectionTestStatus.FAILED,
        modifier = modifier,
        enter = expandHorizontally(expandFrom = Alignment.Start) +
            slideInHorizontally { -it / 3 } +
            fadeIn(),
        exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut()
    ) {
        val success = state.status == ConnectionTestStatus.SUCCESS
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (success) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (success) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = if (success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                androidx.compose.material3.Text(
                    if (success) "连接成功 ${state.latencyMs}ms" else "连接失败 ${state.errorMessage}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (success) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    }
                )
            }
        }
    }
}
