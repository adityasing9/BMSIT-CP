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

    @SuppressLint("SetJavaScriptEnabled")
    fun configureWebView(webView: WebView) {
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
        }

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                url?.let { _currentUrl.value = it }
                _pageLoaded.value = false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                url?.let { _currentUrl.value = it }
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

    fun setDesktopMode(webView: WebView, enabled: Boolean) {
        val settings = webView.settings
        if (enabled) {
            // Windows desktop Chrome UA
            settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            // Force desktop viewport width
            webView.setInitialScale(50) // 50% zoom to fit desktop layout on mobile
        } else {
            settings.userAgentString = null
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            webView.setInitialScale(0) // Reset to default
        }
        webView.reload()
    }
}
