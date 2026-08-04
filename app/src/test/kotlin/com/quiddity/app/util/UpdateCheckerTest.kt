package com.quiddity.app.util

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateCheckerTest {

    @Test
    fun `formal release is higher than previous version beta`() {
        assertTrue(UpdateChecker.compareVersions("1.3.0", "1.2.0-beta") > 0)
        assertTrue(UpdateChecker.compareVersions("1.3.0", "1.2.0-beta.10") > 0)
    }

    @Test
    fun `same version release is higher than its beta`() {
        assertTrue(UpdateChecker.compareVersions("1.3.0", "1.3.0-beta") > 0)
        assertTrue(UpdateChecker.compareVersions("1.3.0-beta", "1.3.0") < 0)
    }

    @Test
    fun `beta numeric segments compare numerically`() {
        assertTrue(UpdateChecker.compareVersions("1.3.0-beta.10", "1.3.0-beta.2") > 0)
        assertTrue(UpdateChecker.compareVersions("1.3.0-beta.2", "1.3.0-beta.10") < 0)
        assertEquals(0, UpdateChecker.compareVersions("1.3.0-beta.2", "1.3.0-beta.2"))
    }

    @Test
    fun `v prefix is ignored`() {
        assertEquals(0, UpdateChecker.compareVersions("v1.3.0", "1.3.0"))
        assertTrue(UpdateChecker.compareVersions("v1.4.0", "1.3.9") > 0)
    }

    @Test
    fun `equal versions are equal`() {
        assertEquals(0, UpdateChecker.compareVersions("1.3.0", "1.3.0"))
        assertEquals(0, UpdateChecker.compareVersions("1.0.0", "1.0"))
    }

    @Test
    fun `unreachable primary url falls back to GitHub latest release`() = runBlocking {
        val primary = "https://quiddity-3by.pages.dev/downloads/quiddity-1.3.1.apk"
        val latest = "https://github.com/jiuan-9/Quiddity-website/releases/download/v1.3.1/quiddity-1.3.1.apk"
        val url = UpdateChecker.resolveApkUrl(
            rawUrl = primary,
            fetchLatestApk = { _, _ -> latest },
            isReachable = { it == latest }
        )
        assertEquals(latest, url)
    }

    @Test
    fun `reachable primary url is preferred over fallback`() = runBlocking {
        val primary = "https://quiddity-3by.pages.dev/downloads/quiddity-1.3.1.apk"
        val latest = "https://github.com/jiuan-9/Quiddity-website/releases/download/v1.3.1/quiddity-1.3.1.apk"
        val url = UpdateChecker.resolveApkUrl(
            rawUrl = primary,
            fetchLatestApk = { _, _ -> latest },
            isReachable = { it == primary }
        )
        assertEquals(primary, url)
    }

    @Test
    fun `homepage download url falls back to GitHub latest release`() = runBlocking {
        val latest = "https://github.com/jiuan-9/Quiddity-website/releases/download/v1.3.1/quiddity-1.3.1.apk"
        val url = UpdateChecker.resolveApkUrl(
            rawUrl = "https://quiddity-3by.pages.dev/",
            fetchLatestApk = { _, _ -> latest },
            isReachable = { it == latest }
        )
        assertEquals(latest, url)
    }
}
