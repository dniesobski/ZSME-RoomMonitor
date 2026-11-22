package com.example.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

data class UpdateCheckOutcome(
    val currentVersion: String,
    val latestRelease: GitHubReleaseInfo?,
    val isUpdateAvailable: Boolean
)

class GitHubUpdateManager(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {

    companion object {
        private val repoSlugRegex = Regex("""^([A-Za-z0-9_.\-]+)/([A-Za-z0-9_.\-]+)$""")
        private val githubUrlRegex = Regex(
            """^(?:https?://)?(?:www\.)?github\.com/([A-Za-z0-9_.\-]+)/([A-Za-z0-9_.\-]+)(?:[/?#].*)?$""",
            RegexOption.IGNORE_CASE
        )
        private val versionNumberGroupRegex = Regex("""\d+(?:\.\d+)*""")

        /**
         * Accepts "owner/repo" or "https://github.com/owner/repo(/releases...)" and returns "owner/repo".
         */
        fun normalizeRepoSlug(rawInput: String): String? {
            val trimmed = rawInput.trim().removeSuffix("/")
            if (trimmed.isEmpty()) return null

            githubUrlRegex.matchEntire(trimmed)?.let { match ->
                val owner = match.groupValues[1]
                val repo = match.groupValues[2].removeSuffix(".git")
                if (owner.isNotBlank() && repo.isNotBlank()) {
                    return "$owner/$repo"
                }
            }

            val withoutGit = trimmed.removeSuffix(".git")
            repoSlugRegex.matchEntire(withoutGit)?.let { match ->
                return "${match.groupValues[1]}/${match.groupValues[2]}"
            }
            return null
        }

        /**
         * Compares version tags like "v1.0.12" vs "v1.0.2" or "1.2.0" vs "1.1.9".
         */
        fun isNewerVersion(remoteTag: String, currentVersion: String): Boolean {
            val remoteParts = extractNumericParts(remoteTag)
            val currentParts = extractNumericParts(currentVersion)

            if (remoteParts.isEmpty() || currentParts.isEmpty()) {
                val cleanRemote = remoteTag.trim().removePrefix("v").removePrefix("V")
                val cleanCurrent = currentVersion.trim().removePrefix("v").removePrefix("V")
                return cleanRemote.isNotEmpty() && !cleanRemote.equals(cleanCurrent, ignoreCase = true)
            }

            val maxLen = maxOf(remoteParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (r != c) return r > c
            }

            // Equal numeric version: non-prerelease (e.g. "v1.0.0") is newer than "-debug"/"-rc"
            val currentIsPre = currentVersion.contains("-")
            val remoteIsPre = remoteTag.contains("-")
            return currentIsPre && !remoteIsPre
        }

        private fun extractNumericParts(version: String): List<Int> {
            val clean = version.trim()
            val match = versionNumberGroupRegex.find(clean) ?: return emptyList()
            return match.value.split(".").mapNotNull { it.toIntOrNull() }
        }

        fun parseReleaseJson(obj: JSONObject): GitHubReleaseInfo {
            val tagName = obj.optString("tag_name").trim()
            val releaseName = obj.optString("name").trim().ifBlank { tagName }
            val body = obj.optString("body").trim()
            val htmlUrl = obj.optString("html_url").trim()
            val publishedAt = obj.optString("published_at").takeIf { it.isNotBlank() && !obj.isNull("published_at") }

            val assets = obj.optJSONArray("assets") ?: JSONArray()
            val apkCandidates = mutableListOf<Triple<String, String, Long>>()

            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name").trim()
                val downloadUrl = asset.optString("browser_download_url").trim()
                val contentType = asset.optString("content_type").trim()
                val size = asset.optLong("size", -1L)

                val isApk = name.endsWith(".apk", ignoreCase = true) ||
                    contentType.equals("application/vnd.android.package-archive", ignoreCase = true)
                if (isApk && downloadUrl.isNotBlank()) {
                    apkCandidates.add(Triple(name, downloadUrl, size))
                }
            }

            val chosenApk = chooseBestApkAsset(apkCandidates)

            return GitHubReleaseInfo(
                tagName = tagName,
                releaseName = releaseName,
                body = body,
                htmlUrl = htmlUrl,
                publishedAt = publishedAt,
                apkAssetName = chosenApk?.first,
                apkDownloadUrl = chosenApk?.second,
                apkSizeBytes = chosenApk?.third?.takeIf { it > 0L }
            )
        }

        fun chooseBestApkAsset(
            apkCandidates: List<Triple<String, String, Long>>
        ): Triple<String, String, Long>? {
            // Prefer release APK over unsigned/debug APKs when multiple assets are attached
            return apkCandidates.firstOrNull { (name, _, _) ->
                !name.contains("unsigned", ignoreCase = true) && !name.contains("debug", ignoreCase = true)
            } ?: apkCandidates.firstOrNull { (name, _, _) ->
                !name.contains("unsigned", ignoreCase = true)
            } ?: apkCandidates.firstOrNull()
        }
    }

    suspend fun checkForUpdate(
        repoInput: String,
        currentVersion: String
    ): Result<UpdateCheckOutcome> = withContext(Dispatchers.IO) {
        val slug = normalizeRepoSlug(repoInput)
            ?: return@withContext Result.failure(
                IllegalArgumentException("Podaj poprawne repozytorium GitHub w formacie właściciel/repozytorium lub pełny link.")
            )

        try {
            val latestUrl = "https://api.github.com/repos/$slug/releases/latest"
            val latestResponse = fetchReleaseObject(latestUrl)

            val release = when {
                latestResponse != null -> {
                    val parsed = parseReleaseJson(latestResponse)
                    if (parsed.apkDownloadUrl != null) parsed else findBestReleaseWithApk(slug) ?: parsed
                }
                else -> findBestReleaseWithApk(slug)
            }

            if (release == null) {
                return@withContext Result.failure(
                    IOException("Nie znaleziono opublikowanych wydań (Releases) w repozytorium $slug.")
                )
            }

            val hasUpdate = isNewerVersion(release.tagName, currentVersion)
            Result.success(
                UpdateCheckOutcome(
                    currentVersion = currentVersion,
                    latestRelease = release,
                    isUpdateAvailable = hasUpdate
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchReleaseObject(apiUrl: String): JSONObject? {
        val request = Request.Builder()
            .url(apiUrl)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "WolneSale-Android-Updater")
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 404) return null
            if (response.code == 403 || response.code == 429) {
                throw IOException("Przekroczono limit zapytań API GitHub (HTTP ${response.code}). Spróbuj ponownie za chwilę.")
            }
            if (!response.isSuccessful) {
                throw IOException("Błąd GitHub API: HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return null
            return JSONObject(body)
        }
    }

    private fun findBestReleaseWithApk(slug: String): GitHubReleaseInfo? {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$slug/releases?per_page=5")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "WolneSale-Android-Updater")
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 404) {
                throw IOException("Nie znaleziono repozytorium $slug na GitHubie (lub jest prywatne).")
            }
            if (response.code == 403 || response.code == 429) {
                throw IOException("Przekroczono limit zapytań API GitHub (HTTP ${response.code}).")
            }
            if (!response.isSuccessful) {
                throw IOException("Błąd pobierania listy wydań GitHub: HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return null
            val arr = JSONArray(body)
            var fallbackFirst: GitHubReleaseInfo? = null
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                if (obj.optBoolean("draft", false)) continue
                val parsed = parseReleaseJson(obj)
                if (fallbackFirst == null) fallbackFirst = parsed
                if (parsed.apkDownloadUrl != null) return parsed
            }
            return fallbackFirst
        }
    }

    suspend fun downloadApk(
        downloadUrl: String,
        preferredFileName: String?,
        cacheDir: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val updatesDir = File(cacheDir, "updates").apply { mkdirs() }
        // Remove previous APK files to keep cache clean
        updatesDir.listFiles()?.forEach { existing ->
            if (existing.isFile) existing.delete()
        }

        val safeName = (preferredFileName ?: "wolne-sale-update.apk")
            .replace(Regex("""[^A-Za-z0-9._\-]"""), "_")
            .let { if (it.endsWith(".apk", ignoreCase = true)) it else "$it.apk" }

        val tempFile = File(updatesDir, "$safeName.part")
        val targetFile = File(updatesDir, safeName)

        try {
            val request = Request.Builder()
                .url(downloadUrl)
                .header("Accept", "application/octet-stream")
                .header("User-Agent", "WolneSale-Android-Updater")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Błąd pobierania pliku APK (HTTP ${response.code})")
                }
                val body = response.body ?: throw IOException("Pusta odpowiedź serwera podczas pobierania APK")
                val totalBytes = body.contentLength()
                var downloaded = 0L

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var lastReportTime = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read

                            val nowMs = System.currentTimeMillis()
                            if (nowMs - lastReportTime >= 100L || (totalBytes > 0 && downloaded >= totalBytes)) {
                                lastReportTime = nowMs
                                onProgress(downloaded, totalBytes)
                            }
                        }
                        output.flush()
                    }
                }

                onProgress(downloaded, if (totalBytes > 0L) totalBytes else downloaded)

                if (downloaded < 1024L || !hasZipMagicBytes(tempFile)) {
                    tempFile.delete()
                    throw IOException("Pobrany plik nie jest prawidłowym pakietem APK.")
                }

                if (targetFile.exists()) targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
            }

            Result.success(targetFile)
        } catch (e: Exception) {
            tempFile.delete()
            Result.failure(e)
        }
    }

    private fun hasZipMagicBytes(file: File): Boolean {
        if (!file.exists() || file.length() < 4) return false
        return runCatching {
            file.inputStream().use { stream ->
                val b0 = stream.read()
                val b1 = stream.read()
                b0 == 0x50 && b1 == 0x4B // 'P', 'K'
            }
        }.getOrDefault(false)
    }
}
