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
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
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

    suspend fun evaluateJavascript(webView: WebView, script: String): String = suspendCoroutine { cont ->
        webView.evaluateJavascript(script) { result ->
            cont.resume(result ?: "")
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
}
