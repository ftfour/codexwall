package ru.ftfour.codexwallpaper

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.ftfour.codexwallpaper.data.Formatters
import java.time.Instant
import java.time.ZoneId

class FormattersTest {
    @Test
    fun formatsRemainingTime() {
        val now = Instant.parse("2026-07-06T13:09:00Z")
        val reset = Instant.parse("2026-07-06T15:27:00Z")

        assertEquals("02:18", Formatters.resetIn(now, reset))
    }

    @Test
    fun expiredRemainingTimeDoesNotGoNegative() {
        assertEquals(
            "00:00",
            Formatters.resetIn(
                Instant.parse("2026-07-06T16:00:00Z"),
                Instant.parse("2026-07-06T15:27:00Z"),
            )
        )
    }

    @Test
    fun formatsResetDate() {
        val text = Formatters.resetDate(
            Instant.parse("2026-07-10T11:20:00Z"),
            use24Hour = true,
            zoneId = ZoneId.of("Europe/Moscow"),
        )

        assertEquals("10 JUL · 14:20", text)
    }
}
