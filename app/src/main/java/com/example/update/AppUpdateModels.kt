package com.example.update

import java.io.File

data class GitHubReleaseInfo(
    val tagName: String,
    val releaseName: String,
    val body: String,
    val htmlUrl: String,
    val publishedAt: String?,
    val apkAssetName: String?,
    val apkDownloadUrl: String?,
    val apkSizeBytes: Long?
)

sealed interface AppUpdateStatus {
    data object Idle : AppUpdateStatus
    data object Checking : AppUpdateStatus
    data class UpToDate(val currentVersion: String, val latestTag: String?) : AppUpdateStatus
    data class UpdateAvailable(val release: GitHubReleaseInfo) : AppUpdateStatus
    data class Downloading(
        val release: GitHubReleaseInfo,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : AppUpdateStatus {
        val progress: Float?
            get() = if (totalBytes > 0L) {
                (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            } else {
                null
            }
    }
    data class ReadyToInstall(
        val release: GitHubReleaseInfo,
        val apkFile: File
    ) : AppUpdateStatus
    data class Error(val message: String) : AppUpdateStatus
}
