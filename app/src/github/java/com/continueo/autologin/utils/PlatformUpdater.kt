package com.continueo.autologin.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object PlatformUpdater {

    const val ENABLE_SELF_UPDATE = true

    private const val GITHUB_REPO_OWNER = "adityasing9"
    private const val GITHUB_REPO_NAME = "BMSIT-CP"
    private const val GITHUB_API_URL =
        "https://api.github.com/repos/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/latest"

    suspend fun checkForUpdates(context: Context, currentVersion: String): Result<UpdateInfo> =
        withContext(Dispatchers.IO) {
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
                    val download = json.optString("downloadUrl", UpdateManager.DIRECT_APK_URL)
                    val html = json.optString("htmlUrl", UpdateManager.RELEASES_PAGE_URL)

                    val hasUpdate = UpdateManager.isNewerVersion(remoteVersion, currentVersion)
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
                    var versionCandidate = UpdateManager.extractVersionString(tag)

                    if (versionCandidate.isBlank()) {
                        try {
                            val pageConn = (URL(location).openConnection() as HttpURLConnection).apply {
                                connectTimeout = 5000
                                readTimeout = 5000
                                setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
                            }
                            if (pageConn.responseCode in 200..299) {
                                val html = pageConn.inputStream.bufferedReader().use { it.readText() }
                                versionCandidate = UpdateManager.extractVersionString(html)
                            }
                        } catch (_: Exception) {}
                    }

                    if (versionCandidate.isNotBlank()) {
                        val hasUpdate = UpdateManager.isNewerVersion(versionCandidate, currentVersion)
                        val info = UpdateInfo(
                            latestVersionName = versionCandidate,
                            releaseTitle = "BMSIT CP v$versionCandidate",
                            releaseNotes = "New release available on GitHub.",
                            downloadUrl = UpdateManager.DIRECT_APK_URL,
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
                val htmlUrl = json.optString("html_url", UpdateManager.RELEASES_PAGE_URL)

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
                    downloadUrl = UpdateManager.DIRECT_APK_URL
                }

                val versionCandidate = UpdateManager.extractVersionString(tagName).ifBlank {
                    UpdateManager.extractVersionString(releaseTitle)
                }

                val updateAvailable = if (versionCandidate.isNotBlank()) {
                    UpdateManager.isNewerVersion(versionCandidate, currentVersion)
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

    suspend fun downloadApkDirect(
        context: Context,
        downloadUrl: String,
        versionName: String,
        onProgress: (percent: Int, bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apkFile = File(updateDir, "BMSIT-CP-v$versionName.apk")
            if (apkFile.exists()) {
                apkFile.delete()
            }

            var currentUrl = downloadUrl
            var connection: HttpURLConnection
            var redirects = 0

            while (true) {
                val url = URL(currentUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", "BMSIT-CP-Android-Updater")
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
}
