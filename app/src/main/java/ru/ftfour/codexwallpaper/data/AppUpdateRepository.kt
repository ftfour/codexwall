package ru.ftfour.codexwallpaper.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URI
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
    suspend fun checkLatestRelease(currentVersion: String, endpointUrl: String = ""): Result<AppUpdate?> = withContext(Dispatchers.IO) {
        runCatching<AppUpdate?> {
            checkCompanionServer(currentVersion, endpointUrl)?.let { return@runCatching it }
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

    private fun checkCompanionServer(currentVersion: String, endpointUrl: String): AppUpdate? {
        val updateUrl = companionUpdateUrl(endpointUrl) ?: return null
        val request = Request.Builder()
            .url(updateUrl)
            .get()
            .header("Accept", "application/json")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (response.code == 404 || response.code == 405) return null
            if (!response.isSuccessful) error("App update HTTP ${response.code}")
            val json = JSONObject(response.body?.string().orEmpty())
            val version = ReleaseVersion.normalized(json.optString("version"))
                ?: return null
            if (!ReleaseVersion.isNewer(version, currentVersion)) return null
            val downloadUrl = json.optString("apk_url").takeIf { it.isNotBlank() } ?: return null
            return AppUpdate(
                version = version,
                pageUrl = json.optString("page_url", downloadUrl),
                downloadUrl = downloadUrl,
            )
        }
    }

    private fun companionUpdateUrl(endpointUrl: String): String? {
        val uri = runCatching { URI(endpointUrl.trim()) }.getOrNull() ?: return null
        val path = uri.path.orEmpty()
        val marker = "/api/codex-limits"
        val index = path.indexOf(marker)
        if (index < 0) return null
        val basePath = path.substring(0, index).trimEnd('/')
        return URI(uri.scheme, uri.userInfo, uri.host, uri.port, "$basePath/app/latest", null, null).toString()
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
