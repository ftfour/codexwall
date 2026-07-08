package ru.ftfour.codexwallpaper

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.ftfour.codexwallpaper.data.CodexJson
import java.time.Instant

class CodexJsonTest {
    @Test
    fun parsesServerJson() {
        val json = javaClass.classLoader!!.getResource("codex_limits_sample.json")!!.readText()
        val limits = CodexJson.parseLimits(json)

        assertEquals(63, limits.fiveHourPercentLeft)
        assertEquals(Instant.parse("2026-07-06T15:27:00Z"), limits.fiveHourResetsAt)
        assertEquals(28, limits.weeklyPercentLeft)
        assertEquals(Instant.parse("2026-07-10T11:20:00Z"), limits.weeklyResetsAt)
        assertEquals(Instant.parse("2026-07-06T13:09:00Z"), limits.updatedAt)
    }

    @Test
    fun clampsPercentValues() {
        assertEquals(0, CodexJson.clampPercent(-4))
        assertEquals(42, CodexJson.clampPercent(42))
        assertEquals(100, CodexJson.clampPercent(144))
    }
}
