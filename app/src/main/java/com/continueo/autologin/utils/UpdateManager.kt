package com.continueo.autologin.utils

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
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
    const val RELEASES_PAGE_URL = "https://github.com/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/latest"

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
        val currentVersion = getCurrentVersionName(context)

        var fallbackInfo: UpdateInfo? = null

        // Strategy 1: Check raw version.json (Fastly CDN, zero rate limit, immune to GitHub 403 API rate limiting)
        try {
            val timestamp = System.currentTimeMillis()
            val rawUrl = URL("https://raw.githubusercontent.com/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/main/version.json?_t=$timestamp")
            val conn = (rawUrl.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                useCaches = false
                requestMethod = "GET"
                setRequestProperty("User-Agent", "ContinueO-Android")
                setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
                setRequestProperty("Pragma", "no-cache")
            }
            if (conn.responseCode in 200..299) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(text)
                val remoteVersion = json.optString("versionName", "")
                val title = json.optString("releaseTitle", "Version $remoteVersion")
                val notes = json.optString("releaseNotes", "")
                val download = json.optString("downloadUrl", DIRECT_APK_URL)
                val html = json.optString("htmlUrl", RELEASES_PAGE_URL)

                val hasUpdate = isNewerVersion(remoteVersion, currentVersion)
                val info = UpdateInfo(
                    latestVersionName = remoteVersion.ifBlank { "Latest" },
                    releaseTitle = title,
                    releaseNotes = notes,
                    downloadUrl = download,
                    htmlUrl = html,
                    isUpdateAvailable = hasUpdate
                )
                if (hasUpdate) {
                    return@withContext Result.success(info)
                }
                // Save as fallback in case subsequent strategies also find no update
                fallbackInfo = info
            }
        } catch (_: Exception) {}

        // Strategy 2: Check GitHub Releases web redirect (No 60 req/hr API rate limit)
        try {
            val redirectUrl = URL("https://github.com/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/latest")
            val conn = (redirectUrl.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                instanceFollowRedirects = false
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
            }
            val location = conn.getHeaderField("Location") ?: ""
            if (location.isNotBlank()) {
                val tag = location.substringAfterLast("/")
                var versionCandidate = extractVersionString(tag)

                // If tag is generic (e.g. "latest"), fetch the release HTML page to extract version from heading
                if (versionCandidate.isBlank()) {
                    try {
                        val pageConn = (URL(location).openConnection() as HttpURLConnection).apply {
                            connectTimeout = 5000
                            readTimeout = 5000
                            setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
                        }
                        if (pageConn.responseCode in 200..299) {
                            val html = pageConn.inputStream.bufferedReader().use { it.readText() }
                            versionCandidate = extractVersionString(html)
                        }
                    } catch (_: Exception) {}
                }

                if (versionCandidate.isNotBlank()) {
                    val hasUpdate = isNewerVersion(versionCandidate, currentVersion)
                    val info = UpdateInfo(
                        latestVersionName = versionCandidate,
                        releaseTitle = "ContinueO Auto Login v$versionCandidate",
                        releaseNotes = "New release available on GitHub.",
                        downloadUrl = DIRECT_APK_URL,
                        htmlUrl = location,
                        isUpdateAvailable = hasUpdate
                    )
                    if (hasUpdate) {
                        return@withContext Result.success(info)
                    }
                    if (fallbackInfo == null) {
                        fallbackInfo = info
                    }
                }
            }
        } catch (_: Exception) {}

        // Strategy 3: Fallback to GitHub REST API
        try {
            val url = URL(GITHUB_API_URL)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "ContinueO-App-Android")
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
            fallbackInfo?.let { return@withContext Result.success(it) }
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

    fun openInBrowser(context: Context, urlString: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlString)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                android.widget.Toast.makeText(context, "No browser found to open link", android.widget.Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {}
        }
    }

    fun openBrowserDownload(context: Context, urlString: String = DIRECT_APK_URL) {
        openInBrowser(context, urlString)
    }

    /**
     * Downloads the update APK directly in-app using Kotlin Coroutines on Dispatchers.IO.
     * Follows HTTP 301/302 redirects (such as GitHub Releases redirects to AWS S3)
     * and streams directly to context.cacheDir/updates/ContinueO-AutoLogin-v$versionName.apk.
     * Reports real-time download percentage and byte counters.
     */
    suspend fun downloadApkDirect(
        context: Context,
        downloadUrl: String,
        versionName: String,
        onProgress: (percent: Int, bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apkFile = File(updateDir, "ContinueO-AutoLogin-v$versionName.apk")
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
                    setRequestProperty("User-Agent", "ContinueO-Android-Updater")
                    setRequestProperty("Accept", "*/*")
                }

                val status = connection.responseCode
                if (status in 300..399) {
                    val newUrl = connection.getHeaderField("Location")
                    if (newUrl != null && redirects < 6) {
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
                    var lastPercent = -1

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

            if (!apkFile.exists() || apkFile.length() == 0L) {
                return@withContext Result.failure(Exception("Downloaded APK file is empty"))
            }

            Result.success(apkFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Checks if the app has permission to trigger unknown package installs on Android 8.0+.
     */
    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Opens the system settings screen for "Install unknown apps" for this app.
     */
    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }

    /**
     * Immediately triggers Android's system package installer for an APK file using FileProvider.
     */
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

    /**
     * Initiates an Over-The-Air (OTA) APK download using Android's system DownloadManager.
     * Displays real-time download progress in the status bar notification.
     * When completed, tapping the system notification triggers package installation.
     * Returns the system download ID, or -1L if initiating the download fails.
     */
    fun startOtaDownload(context: Context, downloadUrl: String, versionName: String): Long {
        return try {
            val destinationFileName = "ContinueO-AutoLogin-v$versionName.apk"
            val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                setTitle("ContinueO Auto Login v$versionName")
                setDescription("Downloading Over-The-Air update...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, destinationFileName)
                setMimeType("application/vnd.android.package-archive")
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
        } catch (_: Exception) {
            -1L
        }
    }

    /**
     * Directly launches the system package installer for an APK downloaded via DownloadManager.
     */
    fun openDownloadedApk(context: Context, downloadId: Long): Boolean {
        return try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val fileUri = dm.getUriForDownloadedFile(downloadId)
            if (fileUri != null) {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(fileUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }
}
