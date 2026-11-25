package com.continueo.autologin.utils

import android.content.Context
import java.io.File

/**
 * F-Droid distribution stub: updates are handled exclusively by the F-Droid client,
 * so self-updating and APK downloading are compiled out in the fdroid flavor.
 */
@Suppress("UNUSED_PARAMETER")
object PlatformUpdater {

    const val ENABLE_SELF_UPDATE = false

    suspend fun checkForUpdates(context: Context, currentVersion: String): Result<UpdateInfo> {
        return Result.success(
            UpdateInfo(
                latestVersionName = currentVersion,
                releaseTitle = "BMSIT CP",
                releaseNotes = "",
                downloadUrl = "",
                htmlUrl = UpdateManager.RELEASES_PAGE_URL,
                isUpdateAvailable = false
            )
        )
    }

    suspend fun downloadApkDirect(
        context: Context,
        downloadUrl: String,
        versionName: String,
        onProgress: (percent: Int, bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): Result<File> {
        return Result.failure(UnsupportedOperationException("In-app updates are managed by F-Droid"))
    }

    fun installApk(context: Context, apkFile: File): Result<Unit> {
        return Result.failure(UnsupportedOperationException("In-app updates are managed by F-Droid"))
    }
}
