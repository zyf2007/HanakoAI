package `fun`.kirari.hanako.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.copyToClipboardWithToast
import `fun`.kirari.hanako.debug.AppDebugLogStore
import `fun`.kirari.hanako.formatDebugTime
import `fun`.kirari.hanako.ui.components.SectionCard

@Composable
fun DebugLogScreen(
    onClearLogs: () -> Unit
) {
    val context = LocalContext.current
    val entries by AppDebugLogStore.entries.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionCard(title = "调试日志") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "这里会保存应用内关键流程日志。复现问题后点“复制全部”发给我即可。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                copyToClipboardWithToast(context, "Hanako Debug Logs", AppDebugLogStore.exportText(), "日志已复制")
                            }
                        ) {
                            Text("复制全部")
                        }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = onClearLogs
                        ) {
                            Text("清空日志")
                        }
                    }
                }
            }
        }

        if (entries.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Text(
                        text = "暂无日志",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            itemsIndexed(
                items = entries.reversed(),
                key = { index, entry -> "${entry.timestamp}-${entry.tag}-${entry.message.hashCode()}-$index" }
            ) { _, entry ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "${formatDebugTime(entry.timestamp)} ${entry.level}/${entry.tag}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            entry.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}
