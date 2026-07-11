package ru.ftfour.codexwallpaper.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AppUpdate(
    val version: String,
    val pageUrl: String,
    val downloadUrl: String,
)

class AppUpdateRepository(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(12, TimeUnit.SECONDS)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun checkLatestRelease(currentVersion: String): Result<AppUpdate?> = withContext(Dispatchers.IO) {
        runCatching<AppUpdate?> {
            val request = Request.Builder()
                .url(LATEST_RELEASE_URL)
                .get()
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Codex-Limits-Wallpaper")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.code == 404) return@runCatching null
                if (!response.isSuccessful) error("HTTP ${response.code}")

                val json = JSONObject(response.body?.string().orEmpty())
                val version = ReleaseVersion.normalized(json.optString("tag_name"))
                    ?: return@runCatching null
                if (!ReleaseVersion.isNewer(version, currentVersion)) return@runCatching null

                AppUpdate(
                    version = version,
                    pageUrl = json.optString("html_url", RELEASES_URL),
                    downloadUrl = firstApkUrl(json) ?: json.optString("html_url", RELEASES_URL),
                )
            }
        }
    }

    private fun firstApkUrl(json: JSONObject): String? {
        val assets = json.optJSONArray("assets") ?: return null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name")
            val url = asset.optString("browser_download_url")
            if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) return url
        }
        return null
    }

    private companion object {
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/ftfour/codexwall/releases/latest"
        const val RELEASES_URL = "https://github.com/ftfour/codexwall/releases"
    }
}

object ReleaseVersion {
    fun normalized(value: String): String? {
        val trimmed = value.trim().removePrefix("v").substringBefore("-")
        return trimmed.takeIf { it.matches(Regex("\\d+(\\.\\d+){0,2}")) }
    }

    fun isNewer(candidate: String, current: String): Boolean {
        val left = parts(candidate) ?: return false
        val right = parts(normalized(current) ?: current) ?: return false
        return left.zip(right).firstOrNull { it.first != it.second }?.let { it.first > it.second } ?: false
    }

    private fun parts(version: String): List<Int>? {
        val normalized = normalized(version) ?: return null
        return normalized.split(".")
            .map { it.toIntOrNull() ?: return null }
            .let { it + List(3 - it.size) { 0 } }
            .take(3)
    }
}
