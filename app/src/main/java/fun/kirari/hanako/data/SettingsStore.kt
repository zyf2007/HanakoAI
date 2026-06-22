package `fun`.kirari.hanako.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import `fun`.kirari.auth.core.AuthorizationSession
import `fun`.kirari.hanako.debug.AppDebugLogStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "hanako_settings")

class SettingsStore(private val context: Context) {
    private val tag = "HanakoSettingsStore"
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val settings: Flow<AppSettings> = context.dataStore.data
        .map { preferences ->
            val raw = preferences[SETTINGS_KEY]
            if (raw.isNullOrBlank()) {
                AppSettings().normalize()
            } else {
                runCatching { json.decodeFromString<AppSettings>(raw).normalize() }.getOrElse { AppSettings().normalize() }
            }
        }
        .flatMapConcat { appSettings ->
            val migrated = migrateHistoryImages(context, appSettings)
            if (migrated != appSettings) {
                flow {
                    persistMigrated(migrated)
                    emit(migrated)
                }
            } else {
                flow { emit(appSettings) }
            }
        }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { preferences ->
            val currentRaw = preferences[SETTINGS_KEY]
            val current = if (currentRaw.isNullOrBlank()) {
                AppSettings().normalize()
            } else {
                runCatching { json.decodeFromString<AppSettings>(currentRaw).normalize() }.getOrElse { AppSettings().normalize() }
            }
            val updated = transform(current).normalize()
            AppDebugLogStore.i(
                tag,
                "update lastResultId=${updated.lastResult?.id} historySize=${updated.history.size} latestHistoryId=${updated.history.firstOrNull()?.id}"
            )
            preferences[SETTINGS_KEY] = json.encodeToString(AppSettings.serializer(), updated)
        }
    }

    suspend fun read(): AppSettings = settings.first()

    suspend fun savePendingKirariAuthorizationSession(session: AuthorizationSession) {
        context.dataStore.edit { preferences ->
            preferences[PENDING_KIRARI_AUTH_SESSION_KEY] =
                json.encodeToString(AuthorizationSession.serializer(), session)
            AppDebugLogStore.i(tag, "savePendingKirariAuthorizationSession state=${session.state.take(8)}")
        }
    }

    suspend fun readPendingKirariAuthorizationSession(): AuthorizationSession? {
        val raw = context.dataStore.data.first()[PENDING_KIRARI_AUTH_SESSION_KEY].orEmpty()
        if (raw.isBlank()) return null
        return runCatching {
            json.decodeFromString(AuthorizationSession.serializer(), raw)
        }.getOrElse { error ->
            AppDebugLogStore.e(tag, "readPendingKirariAuthorizationSession failed", error)
            null
        }
    }

    suspend fun clearPendingKirariAuthorizationSession() {
        context.dataStore.edit { preferences ->
            preferences.remove(PENDING_KIRARI_AUTH_SESSION_KEY)
            AppDebugLogStore.i(tag, "clearPendingKirariAuthorizationSession")
        }
    }

    private suspend fun persistMigrated(settings: AppSettings) {
        context.dataStore.edit { preferences ->
            preferences[SETTINGS_KEY] = json.encodeToString(AppSettings.serializer(), settings)
        }
    }

    companion object {
        private val SETTINGS_KEY = stringPreferencesKey("app_settings")
        private val PENDING_KIRARI_AUTH_SESSION_KEY = stringPreferencesKey("pending_kirari_auth_session")

        private fun migrateHistoryImages(context: Context, settings: AppSettings): AppSettings {
            val needsMigration = settings.history.any { it.screenshotBase64 != null && it.screenshotPath == null } ||
                (settings.lastResult?.let { it.screenshotBase64 != null && it.screenshotPath == null } == true)
            if (!needsMigration) return settings

            val migratedHistory = settings.history.map { result ->
                migrateBase64ToFile(context, result)
            }
            val migratedLastResult = settings.lastResult?.let { migrateBase64ToFile(context, it) }
            return settings.copy(history = migratedHistory, lastResult = migratedLastResult)
        }
    }
}
