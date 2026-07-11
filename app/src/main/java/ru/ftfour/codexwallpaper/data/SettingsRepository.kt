package ru.ftfour.codexwallpaper.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.codexDataStore by preferencesDataStore(name = "codex_wallpaper")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val dataMode = stringPreferencesKey("data_mode")
        val endpointUrl = stringPreferencesKey("endpoint_url")
        val refreshToken = stringPreferencesKey("refresh_token")
        val accentColor = stringPreferencesKey("accent_color")
        val verticalPosition = stringPreferencesKey("vertical_position")
        val horizontalAlignment = stringPreferencesKey("horizontal_alignment")
        val contentScale = stringPreferencesKey("content_scale")
        val refreshInterval = stringPreferencesKey("refresh_interval")
        val showLastUpdated = booleanPreferencesKey("show_last_updated")
        val use24HourFormat = booleanPreferencesKey("use_24_hour_format")
        val limitsJson = stringPreferencesKey("limits_json")
        val demoLimitsJson = stringPreferencesKey("demo_limits_json")
        val lastError = stringPreferencesKey("last_error")
        val stale = booleanPreferencesKey("stale")
    }

    val settingsFlow: Flow<WallpaperSettings> = context.codexDataStore.data.map { prefs ->
        WallpaperSettings(
            dataMode = enumValue(prefs[Keys.dataMode], DataMode.DEMO),
            endpointUrl = prefs[Keys.endpointUrl].orEmpty(),
            refreshToken = prefs[Keys.refreshToken].orEmpty(),
            accentColor = enumValue(prefs[Keys.accentColor], AccentColor.RED),
            verticalPosition = enumValue(prefs[Keys.verticalPosition], VerticalPosition.BOTTOM),
            horizontalAlignment = enumValue(prefs[Keys.horizontalAlignment], HorizontalAlignment.LEFT),
            contentScale = enumValue(prefs[Keys.contentScale], ContentScale.NORMAL),
            refreshInterval = enumValue(prefs[Keys.refreshInterval], RefreshInterval.THIRTY_MINUTES),
            showLastUpdated = prefs[Keys.showLastUpdated] ?: true,
            use24HourFormat = prefs[Keys.use24HourFormat] ?: true,
        )
    }

    val limitsFlow: Flow<CodexLimits> = context.codexDataStore.data.map { prefs ->
        decodeLimits(prefs[Keys.limitsJson]) ?: Defaults.demoLimits
    }

    val demoLimitsFlow: Flow<CodexLimits> = context.codexDataStore.data.map { prefs ->
        decodeLimits(prefs[Keys.demoLimitsJson]) ?: Defaults.demoLimits
    }

    val lastErrorFlow: Flow<String?> = context.codexDataStore.data.map { it[Keys.lastError] }
    val staleFlow: Flow<Boolean> = context.codexDataStore.data.map { it[Keys.stale] ?: false }

    suspend fun updateSettings(settings: WallpaperSettings) {
        context.codexDataStore.edit { prefs ->
            prefs[Keys.dataMode] = settings.dataMode.name
            prefs[Keys.endpointUrl] = settings.endpointUrl
            prefs[Keys.refreshToken] = settings.refreshToken
            prefs[Keys.accentColor] = settings.accentColor.name
            prefs[Keys.verticalPosition] = settings.verticalPosition.name
            prefs[Keys.horizontalAlignment] = settings.horizontalAlignment.name
            prefs[Keys.contentScale] = settings.contentScale.name
            prefs[Keys.refreshInterval] = settings.refreshInterval.name
            prefs[Keys.showLastUpdated] = settings.showLastUpdated
            prefs[Keys.use24HourFormat] = settings.use24HourFormat
        }
    }

    suspend fun saveLimits(limits: CodexLimits, stale: Boolean = false) {
        context.codexDataStore.edit { prefs ->
            prefs[Keys.limitsJson] = CodexJson.encodeLimits(limits)
            prefs[Keys.stale] = stale
            if (!stale) prefs.remove(Keys.lastError)
        }
    }

    suspend fun saveDemoLimits(limits: CodexLimits) {
        context.codexDataStore.edit { prefs ->
            val json = CodexJson.encodeLimits(limits)
            prefs[Keys.demoLimitsJson] = json
            prefs[Keys.limitsJson] = json
            prefs[Keys.stale] = false
            prefs.remove(Keys.lastError)
        }
    }

    suspend fun saveError(message: String) {
        context.codexDataStore.edit { prefs ->
            prefs[Keys.lastError] = message
            prefs[Keys.stale] = true
        }
    }

    private inline fun <reified T : Enum<T>> enumValue(value: String?, fallback: T): T =
        runCatching { if (value == null) fallback else enumValueOf<T>(value) }.getOrDefault(fallback)

    private fun decodeLimits(json: String?): CodexLimits? =
        json?.let { runCatching { CodexJson.parseLimits(it) }.getOrNull() }
}
