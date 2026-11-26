package com.continueo.autologin.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
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
    private const val GITHUB_API_URL =
        "https://api.github.com/repos/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/latest"
    const val DIRECT_APK_URL =
        "https://github.com/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/download/latest/BMSIT-CP.apk"
    const val RELEASES_PAGE_URL =
        "https://github.com/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/latest"

    fun getCurrentVersionName(context: Context): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
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

        // Strategy 1: Check raw version.json (Fastly CDN, zero rate limit)
        try {
            val timestamp = System.currentTimeMillis()
            val rawUrl = URL(
                "https://raw.githubusercontent.com/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/main/version.json?_t=$timestamp"
            )
            val conn = (rawUrl.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                useCaches = false
                requestMethod = "GET"
                setRequestProperty("User-Agent", "BMSIT-CP-Android")
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
                fallbackInfo = info
            }
        } catch (_: Exception) {}

        // Strategy 2: Check GitHub Releases web redirect (No 60 req/hr API rate limit)
        try {
            val redirectUrl = URL(RELEASES_PAGE_URL)
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
                        releaseTitle = "BMSIT CP v$versionCandidate",
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
                setRequestProperty("User-Agent", "BMSIT-CP-App-Android")
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
                Toast.makeText(context, "No browser found to open link", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {}
        }
    }

    /**
     * Launches the direct APK download in the user's browser (Chrome / default browser),
     * avoiding REQUEST_INSTALL_PACKAGES dropper warnings while allowing seamless 1-tap installation.
     */
    fun openBrowserDownload(context: Context, urlString: String = DIRECT_APK_URL) {
        val target = urlString.ifBlank { DIRECT_APK_URL }
        try {
            Toast.makeText(context, "Downloading update via browser...", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
        openInBrowser(context, target)
    }
}
