package ru.ftfour.codexwallpaper.data

import org.json.JSONObject
import java.time.Instant

object CodexJson {
    fun parseLimits(json: String): CodexLimits {
        val obj = JSONObject(json)
        return CodexLimits(
            fiveHourPercentLeft = clampPercent(obj.getInt("five_hour_percent_left")),
            fiveHourResetsAt = parseInstant(obj.getString("five_hour_resets_at")),
            weeklyPercentLeft = clampPercent(obj.getInt("weekly_percent_left")),
            weeklyResetsAt = parseInstant(obj.getString("weekly_resets_at")),
            updatedAt = parseInstant(obj.getString("updated_at")),
        )
    }

    fun encodeLimits(limits: CodexLimits): String = JSONObject()
        .put("five_hour_percent_left", clampPercent(limits.fiveHourPercentLeft))
        .put("five_hour_resets_at", limits.fiveHourResetsAt.toString())
        .put("weekly_percent_left", clampPercent(limits.weeklyPercentLeft))
        .put("weekly_resets_at", limits.weeklyResetsAt.toString())
        .put("updated_at", limits.updatedAt.toString())
        .toString()

    fun clampPercent(value: Int): Int = value.coerceIn(0, 100)

    private fun parseInstant(value: String): Instant = Instant.parse(value)
}
