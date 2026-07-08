package ru.ftfour.codexwallpaper.data

import android.os.Build
import java.net.URI

object UrlValidator {
    fun isAllowedEndpoint(url: String, debug: Boolean): Boolean {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        val host = uri.host?.lowercase() ?: return false
        if (scheme == "https") return true
        if (!debug || scheme != "http") return false
        return host == "localhost" ||
            host == "127.0.0.1" ||
            host == "10.0.2.2" ||
            host.startsWith("192.168.") ||
            host.startsWith("10.") ||
            host.matches(Regex("172\\.(1[6-9]|2\\d|3[0-1])\\..*"))
    }
}
