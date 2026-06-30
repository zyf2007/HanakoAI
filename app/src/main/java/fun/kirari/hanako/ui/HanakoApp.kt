package `fun`.kirari.hanako.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import `fun`.kirari.hanako.HanakoApplication
import `fun`.kirari.hanako.capture.ScreenCaptureManager
import `fun`.kirari.hanako.capture.ScreenCaptureStartResult
import `fun`.kirari.hanako.debug.AppDebugLogStore
import `fun`.kirari.hanako.data.availableProviders
import `fun`.kirari.hanako.overlay.OverlayLaunchMode
import `fun`.kirari.hanako.overlay.OverlayRuntimeState
import `fun`.kirari.hanako.overlay.OverlayService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HanakoApp(viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsState()
    val debugEntries by AppDebugLogStore.entries.collectAsState()
    val kirariAuthMessage by viewModel.kirariAuthMessage.collectAsState()
    val kirariRedirectTarget by viewModel.kirariRedirectTarget.collectAsState()
    val context = LocalContext.current
    val overlayEnabled by OverlayRuntimeState.running.collectAsState()
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var modelSelectionDialogState by remember { mutableStateOf(ModelSelectionDialogState()) }
    val providerModelsApi = remember { HanakoApplication.instance.container.providerModelsApi }

    var currentScreen by rememberSaveable { mutableStateOf(Screen.Hanako) }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    LaunchedEffect(kirariAuthMessage) {
        val message = kirariAuthMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.consumeKirariAuthMessage()
    }

    LaunchedEffect(kirariRedirectTarget) {
        val route = kirariRedirectTarget ?: return@LaunchedEffect
        currentScreen = Screen.Settings
        navController.navigate(route) {
            launchSingleTop = true
            restoreState = true
        }
        viewModel.consumeKirariRedirectTarget()
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = Settings.canDrawOverlays(context)
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BackHandler(enabled = currentRoute == ROUTE_HOME_SHELL && currentScreen == Screen.Settings) {
        currentScreen = Screen.Hanako
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceContainerLow,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            appTitle(currentRoute, currentScreen),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    navigationIcon = {
                        if (currentRoute != null && currentRoute != ROUTE_HOME_SHELL) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            bottomBar = {
                AnimatedVisibility(
                    visible = currentRoute == ROUTE_HOME_SHELL,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    NavigationBar {
                        Screen.entries.forEach { screen ->
                            NavigationBarItem(
                                selected = currentScreen == screen,
                                onClick = { currentScreen = screen },
                                icon = { Icon(screen.icon, contentDescription = screen.title) },
                                label = { Text(screen.title) }
                            )
                        }
                    }
                }
            },
            containerColor = Color.Transparent
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = ROUTE_HOME_SHELL,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(padding),
                enterTransition = {
                    slideInHorizontally { it } + fadeIn()
                },
                exitTransition = {
                    slideOutHorizontally { -it / 2 } + fadeOut()
                },
                popEnterTransition = {
                    slideInHorizontally { -it / 2 } + fadeIn()
                },
                popExitTransition = {
                    slideOutHorizontally { it }
                }
            ) {
                composable(ROUTE_HOME_SHELL) {
                    MainShellScreen(
                        currentScreen = currentScreen,
                        onScreenChange = { currentScreen = it },
                        hanakoContent = {
                            HanakoHomeScreen(
                                settings = settings,
                                overlayEnabled = overlayEnabled,
                                hasOverlayPermission = hasOverlayPermission,
                                onOpenOverlayPermission = {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                    )
                                },
                                onToggleOverlay = { enabled ->
                                    if (enabled) {
                                        when (
                                            val result = ScreenCaptureManager.requestStart(
                                                context = context,
                                                method = settings.screenCaptureMethod,
                                                launchMode = OverlayLaunchMode.NORMAL
                                            )
                                        ) {
                                            ScreenCaptureStartResult.Started -> Unit
                                            is ScreenCaptureStartResult.UserActionRequired -> {
                                                Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                                            }
                                            is ScreenCaptureStartResult.Failed -> {
                                                Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } else {
                                        context.stopService(Intent(context, OverlayService::class.java))
                                        ScreenCaptureManager.stop(context, settings.screenCaptureMethod)
                                    }
                                },
                                onSelectRoute = viewModel::setRoute,
                                onOpenHistory = { navController.navigate(ROUTE_HANAKO_HISTORY) }
                            )
                        },
                        settingsContent = {
                            SettingsMenuScreen(
                                onNavigateProvider = { navController.navigate(ROUTE_SETTINGS_PROVIDER) },
                                onNavigateModel = { navController.navigate(ROUTE_SETTINGS_MODEL) },
                                onNavigateAssistant = { navController.navigate(ROUTE_SETTINGS_ASSISTANT) },
                                onNavigateMore = { navController.navigate(ROUTE_SETTINGS_MORE) },
                                onNavigateDebugLogs = { navController.navigate(ROUTE_SETTINGS_DEBUG_LOGS) }
                            )
                        }
                    )
                }
                composable(ROUTE_HANAKO_HISTORY) {
                    HistorySubScreen(
                        settings = settings,
                        onClearHistory = viewModel::clearHistory,
                        onDeleteHistoryItem = viewModel::deleteHistoryItem,
                        onOpenHistoryDetail = { resultId ->
                            navController.navigate(historyDetailRoute(resultId))
                        }
                    )
                }
                composable("$ROUTE_HANAKO_HISTORY_DETAIL/{$ARG_HISTORY_ID}") { entry ->
                    val resultId = entry.arguments?.getString(ARG_HISTORY_ID)
                    val result = settings.history.firstOrNull { it.id == resultId }
                    HistoryDetailScreen(result = result)
                }
                composable(ROUTE_SETTINGS_PROVIDER) {
                    ProviderSettingsScreen(
                        settings = settings,
                        onAddProvider = viewModel::addProvider,
                        onDeleteProvider = viewModel::deleteProvider,
                        onOpenProvider = { providerId ->
                            viewModel.selectProvider(providerId)
                            navController.navigate(providerDetailRoute(providerId))
                        }
                    )
                }
                composable("$ROUTE_SETTINGS_PROVIDER_DETAIL/{$ARG_PROVIDER_ID}") { entry ->
                    val providerId = entry.arguments?.getString(ARG_PROVIDER_ID)
                    val provider = settings.availableProviders().firstOrNull { it.id == providerId }
                    if (provider != null) {
                        val connectionTestState by viewModel.connectionTestState.collectAsState()
                        val providerMetaState by viewModel.providerMetaState.collectAsState()
                        val kirariAccountState by viewModel.kirariAccountState.collectAsState()
                        ProviderDetailScreen(
                            provider = provider,
                            connectionTestState = connectionTestState,
                            providerMetaState = providerMetaState,
                            kirariAccountState = kirariAccountState,
                            hasKirariClientId = viewModel.hasKirariClientId(),
                            onUpdateProvider = viewModel::updateProvider,
                            onViewModels = {
                                modelSelectionDialogState = modelSelectionDialogState.copy(
                                    providerModelsPreviewId = provider.id
                                )
                            },
                            onTestConnection = viewModel::testProviderConnection,
                            onClearConnectionTest = viewModel::resetConnectionTest,
                            onLoadProviderMeta = viewModel::loadProviderMeta,
                            onClearProviderMeta = viewModel::resetProviderMeta,
                            shouldSuggestKirariAutoSetup = viewModel.shouldSuggestKirariAutoSetup(settings, providerMetaState),
                            onApplyKirariAutoSetup = viewModel::applyKirariAutoSetup,
                            onLoginKirari = {
                                viewModel.startKirariLogin { authorizationUrl ->
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(authorizationUrl))
                                    )
                                }
                            },
                            onLogoutKirari = viewModel::logoutKirari
                        )
                    } else {
                        LaunchedEffect(Unit) { navController.popBackStack() }
                    }
                }
                composable(ROUTE_SETTINGS_MODEL) {
                    ModelSettingsScreen(
                        settings = settings,
                        onPickModel = {
                            modelSelectionDialogState = modelSelectionDialogState.copy(
                                providerPickerTarget = it
                            )
                        }
                    )
                }
                composable(ROUTE_SETTINGS_ASSISTANT) {
                    AssistantSettingsScreen(
                        settings = settings,
                        onAddAssistant = viewModel::addAssistant,
                        onDeleteAssistant = viewModel::deleteAssistant,
                        onOpenAssistant = { assistantId ->
                            viewModel.selectAssistant(assistantId)
                            navController.navigate(assistantDetailRoute(assistantId))
                        }
                    )
                }
                composable("$ROUTE_SETTINGS_ASSISTANT_DETAIL/{$ARG_ASSISTANT_ID}") { entry ->
                    val assistantId = entry.arguments?.getString(ARG_ASSISTANT_ID)
                    val assistant = settings.assistants.firstOrNull { it.id == assistantId }
                    if (assistant != null) {
                        AssistantDetailScreen(
                            assistant = assistant,
                            onUpdateAssistant = viewModel::updateAssistant
                        )
                    } else {
                        LaunchedEffect(Unit) { navController.popBackStack() }
                    }
                }
                composable(ROUTE_SETTINGS_MORE) {
                    MoreSettingsScreen(
                        automationSettings = settings.automation,
                        selectedMethod = settings.screenCaptureMethod,
                        trustAllHttpsCertificates = settings.trustAllHttpsCertificates,
                        kirariSettings = settings.kirari,
                        hasKirariClientId = viewModel.hasKirariClientId(),
                        onToggleCompletionNotification = { enabled ->
                            viewModel.updateAutomationSettings {
                                it.copy(completionNotificationEnabled = enabled)
                            }
                        },
                        onToggleStaticMode = { enabled ->
                            viewModel.updateAutomationSettings {
                                it.copy(staticModeEnabled = enabled)
                            }
                        },
                        onNavigateStaticVibrationSettings = { navController.navigate(ROUTE_SETTINGS_STATIC_VIBRATION) },
                        onUpdateAutomationSettings = { automationSettings ->
                            viewModel.updateAutomationSettings { automationSettings }
                        },
                        onSelectMethod = viewModel::setScreenCaptureMethod,
                        onUpdateTimeoutSeconds = { seconds ->
                            viewModel.updateAutomationSettings {
                                it.copy(autoModeTimeoutSeconds = seconds)
                            }
                        },
                        onToggleTrustAllHttpsCertificates = viewModel::setTrustAllHttpsCertificates,
                        onUpdateKirariServerUrl = { serverUrl ->
                            viewModel.updateKirariSettings { it.copy(serverUrl = serverUrl.trim()) }
                        },
                        onLoginKirari = {
                            viewModel.startKirariLogin { authorizationUrl ->
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(authorizationUrl))
                                )
                            }
                        },
                        onLogoutKirari = viewModel::logoutKirari
                    )
                }
                composable(ROUTE_SETTINGS_STATIC_VIBRATION) {
                    StaticVibrationSettingsScreen(
                        automationSettings = settings.automation,
                        onUpdateSettings = { transform ->
                            viewModel.updateAutomationSettings(transform)
                        }
                    )
                }
                composable(ROUTE_SETTINGS_DEBUG_LOGS) {
                    DebugLogScreen(
                        onClearLogs = viewModel::clearDebugLogs
                    )
                }
            }
        }
    }

    ModelSelectionDialogs(
        state = modelSelectionDialogState,
        settings = settings,
        debugEntries = debugEntries,
        context = context,
        onStateChange = { modelSelectionDialogState = it },
        onUpdateModelSelection = viewModel::updateModelSelection,
        onUpdateModelSelectionWithFavorite = viewModel::updateModelSelectionWithFavorite,
        onToggleFavoriteModel = viewModel::toggleFavoriteModel,
        onSyncLocalOcrInstallation = viewModel::syncLocalOcrInstallation,
        providerModelsApi = providerModelsApi
    )
}
