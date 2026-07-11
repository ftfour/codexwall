package ru.ftfour.codexwallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.ftfour.codexwallpaper.data.ReleaseVersion

class AppUpdateRepositoryTest {
    @Test
    fun normalizesReleaseTags() {
        assertEquals("1.2.3", ReleaseVersion.normalized("v1.2.3"))
        assertEquals("1.2.3", ReleaseVersion.normalized("1.2.3-debug"))
        assertEquals(null, ReleaseVersion.normalized("latest"))
    }

    @Test
    fun comparesSemanticVersions() {
        assertTrue(ReleaseVersion.isNewer("1.1.0", "1.0.9"))
        assertTrue(ReleaseVersion.isNewer("2.0", "1.9.9"))
        assertFalse(ReleaseVersion.isNewer("1.1.0", "1.1.0"))
        assertFalse(ReleaseVersion.isNewer("1.0.9", "1.1.0"))
    }
}
