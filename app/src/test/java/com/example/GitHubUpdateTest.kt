package com.example

import com.example.update.ApkInstaller
import com.example.update.GitHubUpdateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubUpdateTest {

    @Test
    fun `normalizes GitHub repository slugs and URLs accurately`() {
        assertEquals("zsme/wolne-sale", GitHubUpdateManager.normalizeRepoSlug("zsme/wolne-sale"))
        assertEquals("zsme/wolne-sale", GitHubUpdateManager.normalizeRepoSlug("https://github.com/zsme/wolne-sale"))
        assertEquals("zsme/wolne-sale", GitHubUpdateManager.normalizeRepoSlug("https://github.com/zsme/wolne-sale/releases/latest"))
        assertEquals("zsme/wolne-sale", GitHubUpdateManager.normalizeRepoSlug("github.com/zsme/wolne-sale.git"))
        assertNull(GitHubUpdateManager.normalizeRepoSlug("   "))
        assertNull(GitHubUpdateManager.normalizeRepoSlug("only-owner"))
    }

    @Test
    fun `compares semantic and CI run version tags properly`() {
        assertTrue(GitHubUpdateManager.isNewerVersion("v1.0.12", "v1.0.2"))
        assertTrue(GitHubUpdateManager.isNewerVersion("v1.1.0", "v1.0.99"))
        assertTrue(GitHubUpdateManager.isNewerVersion("2.0.0", "v1.9.5"))
        assertTrue(GitHubUpdateManager.isNewerVersion("v1.0.1", "v1.0.1-debug"))

        assertFalse(GitHubUpdateManager.isNewerVersion("v1.0.0", "v1.0.0"))
        assertFalse(GitHubUpdateManager.isNewerVersion("v1.0.2", "v1.0.12"))
        assertFalse(GitHubUpdateManager.isNewerVersion("v0.9.9", "v1.0.0"))
    }

    @Test
    fun `selects release APK asset over debug or unsigned APK assets`() {
        val candidates = listOf(
            Triple("app-debug.apk", "https://example.com/app-debug.apk", 12_000_000L),
            Triple("app-release-unsigned.apk", "https://example.com/unsigned.apk", 13_000_000L),
            Triple("wolne-sale-v1.0.15-release.apk", "https://example.com/wolne-sale-v1.0.15-release.apk", 14_500_000L)
        )

        val chosen = GitHubUpdateManager.chooseBestApkAsset(candidates)
        assertEquals("wolne-sale-v1.0.15-release.apk", chosen?.first)
        assertEquals("https://example.com/wolne-sale-v1.0.15-release.apk", chosen?.second)
        assertEquals(14_500_000L, chosen?.third)
        assertEquals("13.8 MB", ApkInstaller.formatBytes(chosen?.third))
    }
}
