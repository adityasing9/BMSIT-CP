package com.continueo.autologin.ui.viewmodels

import androidx.lifecycle.viewModelScope
import com.continueo.autologin.automation.AttendanceExtractor
import com.continueo.autologin.models.AttendanceSnapshot
import com.continueo.autologin.notifications.AttendanceNotificationManager
import com.continueo.autologin.security.AttendanceSnapshotStore
import com.continueo.autologin.widget.AttendanceWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private val snapshotStore = AttendanceSnapshotStore(application)
    private val webViewManager = WebViewManager()
    private val adapter = ContineoAdapter()
    private val automationEngine = AutomationEngine(webViewManager, adapter)

    private var attendanceSyncJob: Job? = null
    private val _widgetSyncNotice = MutableStateFlow<String?>(null)
    val widgetSyncNotice: StateFlow<String?> = _widgetSyncNotice.asStateFlow()

    init {
        webViewManager.onPageFinishedCallback = { view ->
            if (!automationEngine.automationState.value.isInProgress) {
                syncAttendanceFromPage(view)
            }
        }
    }

    val automationState: StateFlow<AutomationState> = automationEngine.automationState
    val statusMessages: StateFlow<List<StatusMessage>> = automationEngine.statusMessages

    val activeAccount: com.continueo.autologin.models.AccountProfile?
        get() = credentialManager.getActiveAccount()

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
     * Refreshes the current WebView page safely.
     */
    fun refresh(webView: WebView) {
        if (automationEngine.automationState.value.isInProgress) {
            automationEngine.cancelAutomation()
        }
        webView.reload()
    }

    fun clearWidgetSyncNotice() {
        _widgetSyncNotice.value = null
    }

    fun syncAttendanceFromPage(webView: WebView) {
        attendanceSyncJob?.cancel()
        attendanceSyncJob = viewModelScope.launch {
            var syncedAccountIdForSession: String? = null
            var notificationSent = false
            var lastSavedSnapshot: AttendanceSnapshot? = null
            val delaysMs = longArrayOf(0L, 1200L, 3000L, 5500L)
            for (waitMs in delaysMs) {
                if (waitMs > 0L) delay(waitMs)
                val saved = extractAndSaveAttendance(webView)
                if (saved != null) {
                    lastSavedSnapshot = saved
                    if (syncedAccountIdForSession != saved.accountId) {
                        syncedAccountIdForSession = saved.accountId
                        _widgetSyncNotice.value = if (saved.subjects.isNotEmpty()) {
                            "Widget synced (${saved.subjects.size} subjects)"
                        } else {
                            "Widget synced (${saved.displayOverallPercentage ?: 0}%)"
                        }
                    }
                    if (saved.subjects.isNotEmpty()) {
                        postAttendanceNotification(saved)
                        notificationSent = true
                        break
                    }
                }
            }
            if (!notificationSent && lastSavedSnapshot != null) {
                postAttendanceNotification(lastSavedSnapshot)
            }
        }
    }

    private fun postAttendanceNotification(snapshot: AttendanceSnapshot) {
        val accounts = credentialManager.getAllAccounts()
        val activeAccount = accounts.find { it.id == snapshot.accountId } ?: credentialManager.getActiveAccount()
        AttendanceNotificationManager.notifyFromSync(
            context = getApplication(),
            snapshot = snapshot,
            activeAccount = activeAccount,
            accounts = accounts
        )
    }

    private suspend fun extractAndSaveAttendance(webView: WebView): com.continueo.autologin.models.AttendanceSnapshot? {
        val account = credentialManager.getActiveAccount() ?: return null
        return try {
            val rawResult = webViewManager.evaluateJavascript(webView, AttendanceExtractor.EXTRACT_SCRIPT)
            if (rawResult.isBlank() || rawResult == "null") return null
            withContext(Dispatchers.IO) {
                val snapshot = AttendanceExtractor.parseExtractedJson(
                    rawJsResult = rawResult,
                    accountId = account.id,
                    accountName = account.getDisplayName()
                )
                if (snapshot != null && snapshot.hasData) {
                    snapshotStore.saveSnapshot(snapshot)
                    AttendanceWidgetProvider.refreshAllWidgets(getApplication())
                    snapshot
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Cancels the running automation.
     */
    fun cancel() {
        automationEngine.cancelAutomation()
    }

    override fun onCleared() {
        attendanceSyncJob?.cancel()
        webViewManager.onPageFinishedCallback = null
        super.onCleared()
        automationEngine.destroy()
    }
}
