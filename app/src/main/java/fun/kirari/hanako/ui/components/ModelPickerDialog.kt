package `fun`.kirari.hanako.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import `fun`.kirari.hanako.data.ModelProviderConfig
import `fun`.kirari.hanako.network.ProviderModelsApi
import `fun`.kirari.hanako.network.RemoteModelOption

@Composable
fun ModelPickerDialog(
    provider: ModelProviderConfig,
    title: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onCustomModelRequest: (String) -> Unit,
    api: ProviderModelsApi = ProviderModelsApi()
) {
    val models by produceState(initialValue = emptyList<RemoteModelOption>(), provider.id) {
        value = runCatching { api.listModels(provider) }.getOrElse { emptyList() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 560.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = title)

                if (models.isEmpty()) {
                    Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(models) { model ->
                            OutlinedButton(
                                onClick = { onPick(model.id) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(model.displayName)
                                    if (model.displayName != model.id) {
                                        Text(model.id)
                                    }
                                }
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = { onCustomModelRequest(title) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("使用自定义模型")
                }

                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("关闭")
                }
            }
        }
    }
}
