package ru.ftfour.codexwallpaper.data

import java.time.Instant

data class CodexLimits(
    val fiveHourPercentLeft: Int,
    val fiveHourResetsAt: Instant,
    val weeklyPercentLeft: Int,
    val weeklyResetsAt: Instant,
    val updatedAt: Instant,
)

enum class DataMode { DEMO, SERVER }
enum class AccentColor { RED, BLUE, GREEN, WHITE }
enum class VerticalPosition { TOP, CENTER, BOTTOM }
enum class HorizontalAlignment { LEFT, CENTER, RIGHT }
enum class ContentScale { SMALL, NORMAL, LARGE }
enum class RefreshInterval { FIFTEEN_MINUTES, THIRTY_MINUTES, ONE_HOUR, MANUAL }

data class WallpaperSettings(
    val dataMode: DataMode = DataMode.DEMO,
    val endpointUrl: String = "",
    val accentColor: AccentColor = AccentColor.RED,
    val verticalPosition: VerticalPosition = VerticalPosition.BOTTOM,
    val horizontalAlignment: HorizontalAlignment = HorizontalAlignment.LEFT,
    val contentScale: ContentScale = ContentScale.NORMAL,
    val refreshInterval: RefreshInterval = RefreshInterval.THIRTY_MINUTES,
    val showLastUpdated: Boolean = true,
    val use24HourFormat: Boolean = true,
)

data class WallpaperState(
    val limits: CodexLimits,
    val settings: WallpaperSettings,
    val hasFreshData: Boolean,
    val lastError: String? = null,
)

object Defaults {
    val demoLimits = CodexLimits(
        fiveHourPercentLeft = 63,
        fiveHourResetsAt = Instant.parse("2026-07-06T15:27:00Z"),
        weeklyPercentLeft = 28,
        weeklyResetsAt = Instant.parse("2026-07-10T11:20:00Z"),
        updatedAt = Instant.parse("2026-07-06T13:09:00Z"),
    )
}
