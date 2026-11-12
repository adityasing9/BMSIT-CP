package com.continueo.autologin.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val latestVersionName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val htmlUrl: String,
    val isUpdateAvailable: Boolean
)

object UpdateManager {

    private const val GITHUB_REPO_OWNER = "adityasing9"
    private const val GITHUB_REPO_NAME = "BMSIT-CP"
    private const val GITHUB_API_URL = "https://api.github.com/repos/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/latest"
    const val DIRECT_APK_URL = "https://github.com/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/download/latest/ContinueO-AutoLogin.apk"
    const val RELEASES_PAGE_URL = "https://github.com/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/tag/latest"

    fun getCurrentVersionName(context: Context): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }
    }

    suspend fun checkForUpdates(context: Context): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val url = URL(GITHUB_API_URL)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "ContinueO-App")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                return@withContext Result.failure(Exception("HTTP error $responseCode from GitHub"))
            }

            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseText)

            val tagName = json.optString("tag_name", "").trim()
            val releaseTitle = json.optString("name", "").trim()
            val releaseNotes = json.optString("body", "").trim()
            val htmlUrl = json.optString("html_url", RELEASES_PAGE_URL)

            var downloadUrl = ""
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        downloadUrl = asset.optString("browser_download_url", "")
                        break
                    }
                }
            }
            if (downloadUrl.isBlank()) {
                downloadUrl = DIRECT_APK_URL
            }

            // Extract semantic version
            val currentVersion = getCurrentVersionName(context)
            val versionCandidate = extractVersionString(tagName).ifBlank {
                extractVersionString(releaseTitle)
            }

            val updateAvailable = if (versionCandidate.isNotBlank()) {
                isNewerVersion(versionCandidate, currentVersion)
            } else {
                false
            }

            val displayVersion = if (versionCandidate.isNotBlank()) versionCandidate else tagName.ifBlank { "Latest" }

            Result.success(
                UpdateInfo(
                    latestVersionName = displayVersion,
                    releaseTitle = releaseTitle.ifBlank { "Version $displayVersion" },
                    releaseNotes = releaseNotes,
                    downloadUrl = downloadUrl,
                    htmlUrl = htmlUrl,
                    isUpdateAvailable = updateAvailable
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun isNewerVersion(remoteVersion: String, currentVersion: String): Boolean {
        val rClean = remoteVersion.trim().removePrefix("v").removePrefix("V")
        val cClean = currentVersion.trim().removePrefix("v").removePrefix("V")
        val rParts = rClean.split('.').mapNotNull { it.toIntOrNull() }
        val cParts = cClean.split('.').mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(rParts.size, cParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    private fun extractVersionString(text: String): String {
        val regex = Regex("""v?(\d+\.\d+(\.\d+)?)""", RegexOption.IGNORE_CASE)
        val match = regex.find(text)
        return match?.groupValues?.get(1) ?: ""
    }

    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        onProgress: (percent: Int, bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apkFile = File(updateDir, "ContinueO-AutoLogin.apk")
            if (apkFile.exists()) {
                apkFile.delete()
            }

            var currentUrl = downloadUrl
            var connection: HttpURLConnection
            var redirects = 0

            // Follow HTTP redirects (e.g. GitHub Releases redirect to AWS S3)
            while (true) {
                val url = URL(currentUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", "ContinueO-App")
                }

                val status = connection.responseCode
                if (status in 300..399) {
                    val newUrl = connection.getHeaderField("Location")
                    if (newUrl != null && redirects < 5) {
                        currentUrl = newUrl
                        redirects++
                        continue
                    }
                }
                break
            }

            val totalBytes = connection.contentLength.toLong()
            connection.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L
                    var lastPercent = 0

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (totalBytes > 0) {
                            val percent = ((totalRead * 100) / totalBytes).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent, totalRead, totalBytes)
                            }
                        } else {
                            onProgress(-1, totalRead, -1)
                        }
                    }
                    output.flush()
                }
            }

            Result.success(apkFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun installApk(context: Context, apkFile: File): Result<Unit> {
        return try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun openBrowserDownload(context: Context, urlString: String = DIRECT_APK_URL) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlString)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}
