package com.continueo.autologin.ui.viewmodels

import android.app.Application
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel
import com.continueo.autologin.automation.AutomationEngine
import com.continueo.autologin.automation.AutomationState
import com.continueo.autologin.automation.ContineoAdapter
import com.continueo.autologin.automation.StatusMessage
import com.continueo.autologin.automation.WebViewManager
import com.continueo.autologin.security.CredentialManager
import kotlinx.coroutines.flow.StateFlow

/**
 * ViewModel for the Login/WebView screen.
 * Manages the automation engine lifecycle and exposes state to the UI.
 */
class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val credentialManager = CredentialManager(application)
    private val webViewManager = WebViewManager()
    private val adapter = ContineoAdapter()
    private val automationEngine = AutomationEngine(webViewManager, adapter)

    val automationState: StateFlow<AutomationState> = automationEngine.automationState
    val statusMessages: StateFlow<List<StatusMessage>> = automationEngine.statusMessages

    /**
     * Attaches the WebView to the automation engine and starts the login flow.
     */
    private val _isDesktopMode = kotlinx.coroutines.flow.MutableStateFlow(credentialManager.isDesktopMode())
    val isDesktopMode: StateFlow<Boolean> = _isDesktopMode

    /**
     * Attaches the WebView to the automation engine and starts the login flow.
     */
    fun startLogin(webView: WebView) {
        val desktopMode = _isDesktopMode.value
        webViewManager.setDesktopMode(webView, desktopMode, reload = false)
        automationEngine.attachWebView(webView)
        val credentials = credentialManager.loadCredentials()
        if (credentials != null && credentials.isValid()) {
            automationEngine.startAutomation(credentials)
        }
    }

    /**
     * Retries the automation from the beginning.
     */
    fun retry(webView: WebView) {
        val desktopMode = _isDesktopMode.value
        webViewManager.setDesktopMode(webView, desktopMode, reload = false)
        automationEngine.attachWebView(webView)
        automationEngine.retryAutomation()
    }

    /**
     * Toggles between mobile and desktop mode for the WebView.
     * Persists the user preference and safely reloads the page.
     */
    fun toggleDesktopMode(webView: WebView) {
        val nextMode = !_isDesktopMode.value
        _isDesktopMode.value = nextMode
        credentialManager.setDesktopMode(nextMode)

        // Cancel automation if currently running to prevent conflicts with the reload
        if (automationEngine.automationState.value.isInProgress) {
            automationEngine.cancelAutomation()
        }

        webViewManager.setDesktopMode(webView, nextMode, reload = true)
    }

    /**
     * Cancels the running automation.
     */
    fun cancel() {
        automationEngine.cancelAutomation()
    }

    override fun onCleared() {
        super.onCleared()
        automationEngine.destroy()
    }
}
