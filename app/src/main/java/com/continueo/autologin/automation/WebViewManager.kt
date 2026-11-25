package com.continueo.autologin.automation

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.webkit.*
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class WebViewManager {
    private val _pageLoaded = MutableStateFlow(false)
    val pageLoaded: StateFlow<Boolean> = _pageLoaded.asStateFlow()

    private val _currentUrl = MutableStateFlow("")
    val currentUrl: StateFlow<String> = _currentUrl.asStateFlow()

    private var defaultUserAgent: String? = null
    var isDesktopMode: Boolean = true
        private set

    companion object {
        const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        const val DESKTOP_VIEWPORT_WIDTH = 1280
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun configureWebView(webView: WebView, desktopMode: Boolean = isDesktopMode) {
        isDesktopMode = desktopMode
        if (defaultUserAgent == null) {
            defaultUserAgent = webView.settings.userAgentString
        }

        webView.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
        webView.isVerticalScrollBarEnabled = true
        webView.isHorizontalScrollBarEnabled = true

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = WebSettings.LOAD_DEFAULT
            loadsImagesAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = if (isDesktopMode) DESKTOP_USER_AGENT else defaultUserAgent
            allowFileAccess = true
            allowContentAccess = true
        }

        webView.setInitialScale(0)

        // Register Blob download bridge for client-side blob: URLs
        webView.addJavascriptInterface(BlobDownloadBridge(webView.context), "AndroidBlobDownloader")

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                return handleUrlOrDownload(webView, url)
            }

            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url == null) return false
                return handleUrlOrDownload(webView, url)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                url?.let { _currentUrl.value = it }
                _pageLoaded.value = false
            }

            override fun onPageCommitVisible(view: WebView?, url: String?) {
                super.onPageCommitVisible(view, url)
                view?.let { applyViewport(it) }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                url?.let { _currentUrl.value = it }
                view?.let { applyViewport(it) }
                _pageLoaded.value = true
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                return super.onConsoleMessage(consoleMessage)
            }
        }

        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            triggerFileDownload(
                context = webView.context,
                url = url,
                userAgent = userAgent,
                contentDisposition = contentDisposition,
                mimetype = mimetype,
                currentPortalUrl = webView.url
            )
        }
    }

    fun applyViewport(webView: WebView) {
        val script = if (isDesktopMode) {
            """
            (function() {
                try {
                    var meta = document.querySelector('meta[name="viewport"]');
                    if (!meta) {
                        meta = document.createElement('meta');
                        meta.name = 'viewport';
                        (document.head || document.documentElement).appendChild(meta);
                    }
                    meta.setAttribute('content', 'width=$DESKTOP_VIEWPORT_WIDTH');
                } catch(e) {}
            })();
            """.trimIndent()
        } else {
            """
            (function() {
                try {
                    var meta = document.querySelector('meta[name="viewport"]');
                    if (meta) {
                        meta.setAttribute('content', 'width=device-width, initial-scale=1.0');
                    }
                } catch(e) {}
            })();
            """.trimIndent()
        }
        webView.evaluateJavascript(script, null)
    }

    suspend fun evaluateJavascript(webView: WebView, script: String): String =
        withContext(Dispatchers.Main) {
            suspendCoroutine { cont ->
                webView.evaluateJavascript(script) { result ->
                    cont.resume(result ?: "")
                }
            }
        }

    suspend fun waitForPageLoad(timeoutMs: Long): Boolean {
        var elapsed = 0L
        val interval = 100L
        while (!_pageLoaded.value && elapsed < timeoutMs) {
            delay(interval)
            elapsed += interval
        }
        return _pageLoaded.value
    }

    suspend fun getDomViewportWidth(webView: WebView): Int {
        return try {
            val res = evaluateJavascript(
                webView,
                "(function() { return (window.innerWidth || document.documentElement.clientWidth || 0).toString(); })();"
            )
            res.replace("\"", "").trim().toIntOrNull() ?: -1
        } catch (_: Exception) {
            -1
        }
    }

    fun setDesktopMode(webView: WebView, enabled: Boolean, reload: Boolean = false) {
        isDesktopMode = enabled
        if (defaultUserAgent == null) {
            defaultUserAgent = webView.settings.userAgentString
        }

        val settings = webView.settings
        settings.userAgentString = if (enabled) DESKTOP_USER_AGENT else defaultUserAgent
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        webView.setInitialScale(0)

        applyViewport(webView)

        if (reload) {
            webView.reload()
        }
    }

    fun handleUrlOrDownload(webView: WebView, url: String): Boolean {
        if (url.startsWith("blob:")) {
            downloadBlobUrl(webView, url)
            return true
        }

        if (url.startsWith("data:")) {
            downloadDataUrl(webView.context, url)
            return true
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                webView.context.startActivity(intent)
                return true
            } catch (_: Exception) {
                return false
            }
        }

        if (isDownloadableFile(url)) {
            triggerFileDownload(
                context = webView.context,
                url = url,
                userAgent = webView.settings.userAgentString,
                contentDisposition = "",
                mimetype = guessMimeType(url),
                currentPortalUrl = webView.url
            )
            return true
        }

        // Return false for normal portal web pages and form POST submissions.
        // This ensures Android WebView processes POST requests natively with their
        // form body, headers, and cookies, avoiding login breakage.
        return false
    }

    fun triggerFileDownload(
        context: Context,
        url: String,
        userAgent: String,
        contentDisposition: String = "",
        mimetype: String = "",
        currentPortalUrl: String? = null
    ) {
        try {
            var filename = URLUtil.guessFileName(url, contentDisposition, mimetype)
            if (filename.endsWith(".bin", ignoreCase = true) && mimetype.contains("pdf", ignoreCase = true)) {
                filename = filename.substringBeforeLast(".") + ".pdf"
            }
            if (url.contains(".pdf", ignoreCase = true) && !filename.endsWith(".pdf", ignoreCase = true)) {
                filename = "$filename.pdf"
            }

            val request = DownloadManager.Request(Uri.parse(url)).apply {
                if (mimetype.isNotBlank()) {
                    setMimeType(mimetype)
                }
                val cookies = CookieManager.getInstance().getCookie(url)
                    ?: CookieManager.getInstance().getCookie("https://student.bmsit.ac.in/")
                if (!cookies.isNullOrBlank()) {
                    addRequestHeader("Cookie", cookies)
                }
                addRequestHeader("User-Agent", userAgent)
                currentPortalUrl?.let { addRequestHeader("Referer", it) }
                setDescription("Downloading file from BMSIT portal...")
                setTitle(filename)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Toast.makeText(context, "Downloading $filename to Downloads...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun isDownloadableFile(url: String): Boolean {
        val lower = url.lowercase()
        val fileExtensions = listOf(
            ".pdf", ".docx", ".doc", ".xlsx", ".xls", ".zip", ".rar",
            ".csv", ".pptx", ".ppt", ".txt", ".tar", ".gz", ".7z"
        )
        val cleanUrl = lower.substringBefore("?").substringBefore("#")
        if (fileExtensions.any { ext -> cleanUrl.endsWith(ext) || lower.contains("$ext?") }) {
            return true
        }
        if (lower.contains("download_file") || lower.contains("get_file") || lower.contains("action=download")) {
            return true
        }
        return false
    }

    fun guessMimeType(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains(".pdf") -> "application/pdf"
            lower.contains(".docx") -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            lower.contains(".doc") -> "application/msword"
            lower.contains(".xlsx") -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            lower.contains(".xls") -> "application/vnd.ms-excel"
            lower.contains(".zip") -> "application/zip"
            else -> "*/*"
        }
    }

    class BlobDownloadBridge(private val context: Context) {
        @JavascriptInterface
        fun processBlob(base64Data: String, mimeType: String, suggestedName: String) {
            try {
                val cleanBase64 = if (base64Data.contains(",")) {
                    base64Data.substringAfter(",")
                } else {
                    base64Data
                }
                val fileBytes = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
                val fileName = if (suggestedName.isNotBlank()) suggestedName else "BMSIT_Document.pdf"

                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val outputFile = java.io.File(downloadsDir, fileName)
                outputFile.writeBytes(fileBytes)

                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.addCompletedDownload(
                    fileName,
                    "Downloaded via BMSIT CP",
                    true,
                    mimeType.ifBlank { "application/pdf" },
                    outputFile.absolutePath,
                    fileBytes.size.toLong(),
                    true
                )

                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context, "Downloaded $fileName to Downloads folder!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context, "Download error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun downloadBlobUrl(webView: WebView, blobUrl: String, filename: String = "BMSIT_Document.pdf") {
        val script = """
            (function() {
                try {
                    var xhr = new XMLHttpRequest();
                    xhr.open('GET', '$blobUrl', true);
                    xhr.responseType = 'blob';
                    xhr.onload = function() {
                        if (this.status === 200 || this.status === 0) {
                            var reader = new FileReader();
                            reader.readAsDataURL(this.response);
                            reader.onloadend = function() {
                                if (window.AndroidBlobDownloader) {
                                    window.AndroidBlobDownloader.processBlob(reader.result, 'application/pdf', '$filename');
                                }
                            };
                        }
                    };
                    xhr.send();
                } catch(e) {}
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    fun downloadDataUrl(context: Context, dataUrl: String) {
        try {
            val base64Data = dataUrl.substringAfter(",")
            val mimeType = dataUrl.substringBefore(";").substringAfter("data:")
            val ext = if (mimeType.contains("pdf")) "pdf" else "bin"
            val fileName = "BMSIT_Document_${System.currentTimeMillis()}.$ext"
            val fileBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val outputFile = java.io.File(downloadsDir, fileName)
            outputFile.writeBytes(fileBytes)

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.addCompletedDownload(
                fileName,
                "Downloaded via BMSIT CP",
                true,
                mimeType,
                outputFile.absolutePath,
                fileBytes.size.toLong(),
                true
            )
            Toast.makeText(context, "Downloaded $fileName to Downloads folder!", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
    }
}
