package com.continueo.autologin.automation

import android.webkit.WebView
import com.continueo.autologin.models.Credentials
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Status message displayed during automation progress.
 * Does NOT contain actual credential values.
 */
data class StatusMessage(
    val text: String,
    val isSuccess: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Core automation controller that manages the login flow state machine.
 *
 * States flow:
 * IDLE → LOADING_PORTAL → DETECTING_PAGE → FILLING_CREDENTIALS →
 * SUBMITTING_LOGIN → WAITING_FOR_LOGIN_RESULT → FILLING_ID_CARD →
 * SUBMITTING_ID_CARD → SUCCESS
 *
 * Error at any point → ERROR or MANUAL_INTERVENTION_REQUIRED
 */
class AutomationEngine(
    private val webViewManager: WebViewManager,
    private val adapter: ContineoAdapter
) {
    private val _automationState = MutableStateFlow<AutomationState>(AutomationState.IDLE)
    val automationState: StateFlow<AutomationState> = _automationState.asStateFlow()

    private val _statusMessages = MutableStateFlow<List<StatusMessage>>(emptyList())
    val statusMessages: StateFlow<List<StatusMessage>> = _statusMessages.asStateFlow()

    private var automationJob: Job? = null
    private var webView: WebView? = null
    private var credentials: Credentials? = null

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        private const val PAGE_LOAD_TIMEOUT = 20_000L
        private const val ELEMENT_DETECT_TIMEOUT = 10_000L
        private const val POST_SUBMIT_DELAY = 2_000L
        private const val DETECTION_INTERVAL = 500L
    }

    fun attachWebView(wv: WebView) {
        this.webView = wv
        webViewManager.configureWebView(wv)
    }

    fun startAutomation(creds: Credentials) {
        if (webView == null) {
            addStatus("WebView not ready", false)
            return
        }
        this.credentials = creds
        _statusMessages.value = emptyList()
        automationJob?.cancel()
        automationJob = scope.launch {
            runAutomationSequence()
        }
    }

    fun cancelAutomation() {
        automationJob?.cancel()
        _automationState.value = AutomationState.IDLE
        addStatus("Automation cancelled")
    }

    fun retryAutomation() {
        credentials?.let { startAutomation(it) } ?: run {
            addStatus("No credentials available for retry", false)
        }
    }

    fun destroy() {
        automationJob?.cancel()
        scope.cancel()
    }

    private suspend fun runAutomationSequence() {
        val wv = webView ?: return
        try {
            // Step 1: Load the portal
            _automationState.value = AutomationState.LOADING_PORTAL
            addStatus("Opening ContinueO…")
            withContext(Dispatchers.Main) {
                wv.loadUrl(ContineoSelectors.PORTAL_URL)
            }

            // Step 2: Wait for page load
            val loaded = webViewManager.waitForPageLoad(PAGE_LOAD_TIMEOUT)
            if (!loaded) {
                throw AutomationException("Portal did not load. Check your internet connection.")
            }
            addStatus("✓ Portal loaded")

            // Give DOM a moment to fully render
            delay(1500)

            // Step 3: Detect what page we're on
            _automationState.value = AutomationState.DETECTING_PAGE
            addStatus("Detecting current page…")

            val isLoginPage = waitForCondition(ELEMENT_DETECT_TIMEOUT) {
                parseBooleanResult(webViewManager.evaluateJavascript(wv, adapter.detectLoginPage()))
            }

            if (isLoginPage) {
                // We're on Page 1 — do full flow
                handleLoginFlow(wv)
            } else {
                // Check if we're on ID Card page (maybe already past login)
                val isIdCardPage = parseBooleanResult(
                    webViewManager.evaluateJavascript(wv, adapter.detectIdCardPage())
                )
                if (isIdCardPage) {
                    handleIdCardFlow(wv)
                } else {
                    // Check if already logged in
                    val isSuccess = parseBooleanResult(
                        webViewManager.evaluateJavascript(wv, adapter.detectSuccessPage())
                    )
                    if (isSuccess) {
                        _automationState.value = AutomationState.SUCCESS
                        addStatus("✓ Already logged in!")
                    } else {
                        throw AutomationException(
                            "Unable to detect the ContinueO login page.\nThe portal may have changed."
                        )
                    }
                }
            }

        } catch (e: CancellationException) {
            throw e // Don't catch coroutine cancellation
        } catch (e: AutomationException) {
            _automationState.value = AutomationState.ERROR
            addStatus("⚠ ${e.message}", false)
        } catch (e: Exception) {
            _automationState.value = AutomationState.ERROR
            addStatus("⚠ Unexpected error: ${e.message}", false)
        }
    }

    private suspend fun handleLoginFlow(wv: WebView) {
        val creds = credentials ?: throw AutomationException("Credentials not available")

        // Fill login page
        _automationState.value = AutomationState.FILLING_CREDENTIALS
        addStatus("Filling login details…")

        val fillResult = executeAndParse(wv, adapter.fillLoginPage(creds))
        if (!fillResult.optBoolean("success", false)) {
            throw AutomationException(
                fillResult.optString("error", "Failed to fill login details")
            )
        }
        addStatus("✓ Login details entered")

        // Submit login form
        _automationState.value = AutomationState.SUBMITTING_LOGIN
        addStatus("Submitting login…")

        val submitResult = executeAndParse(wv, adapter.submitLoginForm())
        if (!submitResult.optBoolean("success", false)) {
            throw AutomationException(
                submitResult.optString("error", "Failed to submit login form")
            )
        }

        // Wait for post-login page
        _automationState.value = AutomationState.WAITING_FOR_LOGIN_RESULT
        addStatus("Waiting for response…")

        val postLoginLoaded = webViewManager.waitForPageLoad(PAGE_LOAD_TIMEOUT)
        if (!postLoginLoaded) {
            throw AutomationException("Timeout waiting for login response")
        }

        delay(POST_SUBMIT_DELAY)

        // Detect what's next
        val isIdCardPage = waitForCondition(ELEMENT_DETECT_TIMEOUT) {
            parseBooleanResult(webViewManager.evaluateJavascript(wv, adapter.detectIdCardPage()))
        }

        if (isIdCardPage) {
            addStatus("✓ Login submitted")
            handleIdCardFlow(wv)
        } else {
            // Maybe we went straight to success (no ID card page)
            val isSuccess = parseBooleanResult(
                webViewManager.evaluateJavascript(wv, adapter.detectSuccessPage())
            )
            if (isSuccess) {
                _automationState.value = AutomationState.SUCCESS
                addStatus("✓ Login complete!")
            } else {
                // Maybe the login failed (wrong credentials) and we're back on login page
                val stillOnLogin = parseBooleanResult(
                    webViewManager.evaluateJavascript(wv, adapter.detectLoginPage())
                )
                if (stillOnLogin) {
                    throw AutomationException(
                        "Login may have failed. Please verify your credentials."
                    )
                } else {
                    // Could be a CAPTCHA or OTP page
                    _automationState.value = AutomationState.MANUAL_INTERVENTION_REQUIRED
                    addStatus("⚠ Manual verification required", false)
                }
            }
        }
    }

    private suspend fun handleIdCardFlow(wv: WebView) {
        val creds = credentials ?: throw AutomationException("Credentials not available")

        addStatus("Opening ID card verification…")

        // Fill ID card number with retry loop (page may still be rendering)
        _automationState.value = AutomationState.FILLING_ID_CARD
        addStatus("Filling ID card number…")

        var filled = false
        var lastError = "ID Card input not found"
        val maxAttempts = 15 // 15 * 500ms = 7.5 seconds
        for (attempt in 1..maxAttempts) {
            val fillResult = executeAndParse(wv, adapter.fillIdCardPage(creds.idCardNumber))
            if (fillResult.optBoolean("success", false)) {
                filled = true
                break
            } else {
                lastError = fillResult.optString("error", lastError)
            }
            delay(500)
        }

        if (!filled) {
            throw AutomationException(lastError)
        }
        addStatus("✓ ID card number entered")

        // Submit ID card form
        _automationState.value = AutomationState.SUBMITTING_ID_CARD
        addStatus("Completing login…")

        val submitResult = executeAndParse(wv, adapter.submitIdCardForm())
        if (!submitResult.optBoolean("success", false)) {
            throw AutomationException(
                submitResult.optString("error", "Failed to submit ID card form")
            )
        }

        // Wait for final page
        val finalLoaded = webViewManager.waitForPageLoad(PAGE_LOAD_TIMEOUT)
        if (!finalLoaded) {
            throw AutomationException("Timeout waiting for final page")
        }

        delay(POST_SUBMIT_DELAY)

        _automationState.value = AutomationState.SUCCESS
        addStatus("✓ Done — You're logged in!")
    }

    /**
     * Waits for a condition to become true within a timeout.
     * Polls at regular intervals.
     */
    private suspend fun waitForCondition(
        timeoutMs: Long,
        checkFn: suspend () -> Boolean
    ): Boolean {
        var elapsed = 0L
        while (elapsed < timeoutMs) {
            if (checkFn()) return true
            delay(DETECTION_INTERVAL)
            elapsed += DETECTION_INTERVAL
        }
        return checkFn() // One last check
    }

    private suspend fun executeAndParse(wv: WebView, script: String): JSONObject {
        val resultStr = webViewManager.evaluateJavascript(wv, script)
        return parseJsonResult(resultStr)
    }

    private fun addStatus(text: String, isSuccess: Boolean = true) {
        val msg = StatusMessage(text, isSuccess)
        _statusMessages.value = _statusMessages.value + msg
    }

    private fun parseJsonResult(jsonStr: String): JSONObject {
        return try {
            val cleanJson = if (jsonStr.startsWith("\"") && jsonStr.endsWith("\"")) {
                jsonStr.substring(1, jsonStr.length - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
            } else {
                jsonStr
            }
            JSONObject(cleanJson)
        } catch (e: Exception) {
            JSONObject().apply {
                put("success", false)
                put("error", "Invalid response from portal")
            }
        }
    }

    private fun parseBooleanResult(str: String): Boolean {
        val cleanStr = str.replace("\"", "").trim()
        return cleanStr.equals("true", ignoreCase = true)
    }
}

/**
 * Custom exception for automation failures.
 * Messages should never contain actual credential values.
 */
class AutomationException(message: String) : Exception(message)

/**
 * Automation states used by the engine.
 * Separate from the models.AutomationState sealed class — this is the
 * engine's internal state representation.
 */
enum class AutomationState(val displayMessage: String) {
    IDLE("Ready to start"),
    LOADING_PORTAL("Opening login portal..."),
    DETECTING_PAGE("Detecting page..."),
    FILLING_CREDENTIALS("Entering login details..."),
    SUBMITTING_LOGIN("Submitting login..."),
    WAITING_FOR_LOGIN_RESULT("Waiting for login response..."),
    FILLING_ID_CARD("Entering ID card number..."),
    SUBMITTING_ID_CARD("Submitting verification..."),
    SUCCESS("Login completed successfully"),
    ERROR("Automation error"),
    MANUAL_INTERVENTION_REQUIRED("Manual intervention required");

    val isTerminal: Boolean
        get() = this == SUCCESS || this == ERROR || this == MANUAL_INTERVENTION_REQUIRED

    val isInProgress: Boolean
        get() = !isTerminal && this != IDLE
}
