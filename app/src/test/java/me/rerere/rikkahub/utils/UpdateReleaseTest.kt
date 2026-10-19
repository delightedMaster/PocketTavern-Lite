package me.rerere.rikkahub.utils

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateReleaseTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `release uses PocketTavern notes and only downloadable APK assets`() {
        val info = json.decodeFromString<GitHubRelease>(
            """
            {
              "tag_name": "v0.1.1",
              "body": "PocketTavern-Lite updates",
              "published_at": "2026-10-19T09:39:32Z",
              "html_url": "https://github.com/delightedMaster/PocketTavern-Lite/releases/tag/v0.1.1",
              "assets": [
                {"name": "pockettavern.apk", "browser_download_url": "https://github.com/delightedMaster/PocketTavern-Lite/releases/download/v0.1.1/pockettavern.apk", "size": 123},
                {"name": "checksums.txt", "browser_download_url": "https://example.com/checksums.txt"},
                {"name": "missing.apk", "browser_download_url": ""}
              ],
              "draft": false
            }
            """.trimIndent()
        ).toUpdateInfo()

        assertEquals("0.1.1", info.version)
        assertEquals("PocketTavern-Lite updates", info.changelog)
        assertEquals("2026-10-19T09:39:32Z", info.publishedAt)
        assertEquals("pockettavern.apk", info.downloads.single().name)
        assertEquals("123", info.downloads.single().size)
        assertTrue(info.downloads.single().isApk)
    }

    @Test
    fun `release without APK opens its release page instead of downloading HTML`() {
        val url = "https://github.com/delightedMaster/PocketTavern-Lite/releases/tag/v0.1.1"
        val download = GitHubRelease(tag_name = "V0.1.1", html_url = url).toUpdateInfo().downloads.single()

        assertEquals(url, download.url)
        assertFalse(download.isApk)
        assertTrue(download.name.contains("PocketTavern-Lite"))
    }

    @Test
    fun `release without assets or page does not offer an empty download URL`() {
        assertTrue(GitHubRelease(tag_name = "v0.1.1").toUpdateInfo().downloads.isEmpty())
    }

    @Test
    fun `APK extension is case insensitive`() {
        assertTrue(UpdateDownload("PocketTavern.APK", "https://example.com/app.apk", "123").isApk)
    }
}
