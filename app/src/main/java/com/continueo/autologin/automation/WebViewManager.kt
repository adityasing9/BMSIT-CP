package com.continueo.autologin.automation

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.*
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
        }

        webView.setInitialScale(0)

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
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
}
