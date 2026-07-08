package ru.ftfour.codexwallpaper.data

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Duration
import java.util.concurrent.TimeUnit

class CodexLimitsRepository(
    private val context: Context,
    private val settingsRepository: SettingsRepository = SettingsRepository(context),
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(12, TimeUnit.SECONDS)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build(),
) {
    val wallpaperStateFlow: Flow<WallpaperState> = combine(
        settingsRepository.settingsFlow,
        settingsRepository.limitsFlow,
        settingsRepository.demoLimitsFlow,
        settingsRepository.staleFlow,
        settingsRepository.lastErrorFlow,
    ) { settings, serverLimits, demoLimits, stale, error ->
        val demoMode = settings.dataMode == DataMode.DEMO
        WallpaperState(
            limits = if (demoMode) demoLimits else serverLimits,
            settings = settings,
            hasFreshData = demoMode || !stale,
            lastError = if (demoMode) null else error,
        )
    }

    suspend fun refreshFromConfiguredServer(): Result<CodexLimits> {
        val settings = settingsRepository.settingsFlow.first()
        if (settings.dataMode != DataMode.SERVER) {
            return Result.success(settingsRepository.demoLimitsFlow.first())
        }
        return refreshFromUrl(settings.endpointUrl)
    }

    suspend fun testConnection(url: String): Result<CodexLimits> = refreshFromUrl(url, save = false)

    suspend fun refreshFromUrl(url: String, save: Boolean = true): Result<CodexLimits> = withContext(Dispatchers.IO) {
        if (!UrlValidator.isAllowedEndpoint(url, BuildConfigLike.DEBUG)) {
            val error = "Invalid URL. Use HTTPS, or local HTTP in debug builds."
            if (save) settingsRepository.saveError(error)
            return@withContext Result.failure(IllegalArgumentException(error))
        }

        val request = Request.Builder()
            .url(url.trim())
            .get()
            .header("Accept", "application/json")
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body?.string().orEmpty()
                val parsed = CodexJson.parseLimits(body)
                if (save) {
                    settingsRepository.saveLimits(parsed, stale = false)
                    context.sendBroadcast(Intent(ACTION_CODEX_DATA_CHANGED).setPackage(context.packageName))
                }
                Result.success(parsed)
            }
        } catch (t: Throwable) {
            val message = t.message?.takeIf { it.isNotBlank() } ?: "Connection failed"
            if (save) settingsRepository.saveError(message)
            Result.failure(t)
        }
    }

    fun isStale(updatedAge: Duration, maxAge: Duration): Boolean = updatedAge > maxAge

    companion object {
        const val ACTION_CODEX_DATA_CHANGED = "ru.ftfour.codexwallpaper.CODEX_DATA_CHANGED"
    }
}

private object BuildConfigLike {
    val DEBUG: Boolean = ru.ftfour.codexwallpaper.BuildConfig.DEBUG
}
