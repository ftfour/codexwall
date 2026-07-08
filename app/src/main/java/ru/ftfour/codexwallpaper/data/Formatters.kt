package ru.ftfour.codexwallpaper.data

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Formatters {
    fun resetIn(now: Instant, target: Instant): String {
        val remaining = Duration.between(now, target)
        if (remaining.isNegative || remaining.isZero) return "00:00"
        val minutes = remaining.toMinutes()
        val hours = minutes / 60
        val mins = minutes % 60
        return "%02d:%02d".format(Locale.US, hours, mins)
    }

    fun resetDate(instant: Instant, use24Hour: Boolean, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val pattern = if (use24Hour) "dd MMM · HH:mm" else "dd MMM · h:mm a"
        return DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)
            .withZone(zoneId)
            .format(instant)
            .uppercase(Locale.ENGLISH)
    }

    fun time(instant: Instant, use24Hour: Boolean, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val pattern = if (use24Hour) "HH:mm" else "h:mm a"
        return DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)
            .withZone(zoneId)
            .format(instant)
            .uppercase(Locale.ENGLISH)
    }
}
