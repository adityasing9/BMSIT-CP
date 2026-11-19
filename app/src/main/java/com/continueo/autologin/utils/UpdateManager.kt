package com.continueo.autologin.utils

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
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
        val currentVersion = getCurrentVersionName(context)

        // Strategy 1: Check raw version.json (Fastly CDN, zero rate limit, immune to GitHub 403 API rate limiting)
        try {
            val rawUrl = URL("https://raw.githubusercontent.com/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/main/version.json")
            val conn = (rawUrl.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "ContinueO-Android")
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
                return@withContext Result.success(
                    UpdateInfo(
                        latestVersionName = remoteVersion.ifBlank { "Latest" },
                        releaseTitle = title,
                        releaseNotes = notes,
                        downloadUrl = download,
                        htmlUrl = html,
                        isUpdateAvailable = hasUpdate
                    )
                )
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
                val versionCandidate = extractVersionString(tag)
                if (versionCandidate.isNotBlank()) {
                    val hasUpdate = isNewerVersion(versionCandidate, currentVersion)
                    return@withContext Result.success(
                        UpdateInfo(
                            latestVersionName = versionCandidate,
                            releaseTitle = "ContinueO Auto Login v$versionCandidate",
                            releaseNotes = "New release available on GitHub.",
                            downloadUrl = DIRECT_APK_URL,
                            htmlUrl = location,
                            isUpdateAvailable = hasUpdate
                        )
                    )
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
