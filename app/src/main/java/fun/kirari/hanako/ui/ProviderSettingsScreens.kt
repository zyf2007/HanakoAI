package `fun`.kirari.hanako.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.data.AppSettings
import `fun`.kirari.hanako.data.KIRARI_PROVIDER_ID
import `fun`.kirari.hanako.data.ModelProviderConfig
import `fun`.kirari.hanako.data.availableProviders
import `fun`.kirari.hanako.data.displayName

@Composable
fun ProviderSettingsScreen(
    settings: AppSettings,
    onAddProvider: () -> Unit,
    onDeleteProvider: (String) -> Unit,
    onOpenProvider: (String) -> Unit
) {
    var deleteTargetId by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "模型提供方",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(onClick = onAddProvider) {
                    Text("新增")
                }
            }
        }
        items(settings.availableProviders(), key = { it.id }) { provider ->
            ProviderListItem(
                provider = provider,
                onOpenProvider = onOpenProvider,
                onRequestDelete = { deleteTargetId = it }
            )
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }

    val deleteTarget = settings.availableProviders().firstOrNull { it.id == deleteTargetId }
    if (deleteTarget != null) {
        DeleteConfirmDialog(
            title = "删除提供方",
            message = "确认删除 ${deleteTarget.name}？",
            onDismiss = { deleteTargetId = null },
            onConfirm = {
                onDeleteProvider(deleteTarget.id)
                deleteTargetId = null
            }
        )
    }
}

@Composable
fun ProviderDetailScreen(
    provider: ModelProviderConfig,
    connectionTestState: ConnectionTestState,
    providerMetaState: ProviderMetaState,
    kirariAccountState: KirariAccountState,
    hasKirariClientId: Boolean,
    onUpdateProvider: (ModelProviderConfig) -> Unit,
    onViewModels: () -> Unit,
    onTestConnection: (ModelProviderConfig) -> Unit,
    onClearConnectionTest: () -> Unit,
    onLoadProviderMeta: (ModelProviderConfig) -> Unit,
    onClearProviderMeta: () -> Unit,
    onLoginKirari: () -> Unit,
    onLogoutKirari: () -> Unit
) {
    DisposableEffect(provider.id) {
        onDispose {
            onClearConnectionTest()
            onClearProviderMeta()
        }
    }
    LaunchedEffect(provider.id) {
        if (provider.id == KIRARI_PROVIDER_ID) {
            onLoadProviderMeta(provider)
        }
    }

    if (provider.id == KIRARI_PROVIDER_ID) {
        KirariProviderDetailScreen(
            provider = provider,
            providerMetaState = providerMetaState,
            kirariAccountState = kirariAccountState,
            connectionTestState = connectionTestState,
            hasKirariClientId = hasKirariClientId,
            onViewModels = onViewModels,
            onTestConnection = onTestConnection,
            onLoginKirari = onLoginKirari,
            onLogoutKirari = onLogoutKirari
        )
    } else {
        GenericProviderDetailScreen(
            provider = provider,
            connectionTestState = connectionTestState,
            onUpdateProvider = onUpdateProvider,
            onViewModels = onViewModels,
            onTestConnection = onTestConnection
        )
    }
}

@Composable
private fun ProviderListItem(
    provider: ModelProviderConfig,
    onOpenProvider: (String) -> Unit,
    onRequestDelete: (String) -> Unit
) {
    Surface(
        modifier = Modifier.combinedClickable(
            onClick = { onOpenProvider(provider.id) },
            onLongClick = {
                if (provider.id != KIRARI_PROVIDER_ID) {
                    onRequestDelete(provider.id)
                }
            }
        ),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    provider.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    provider.kind.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            androidx.compose.material3.Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}
