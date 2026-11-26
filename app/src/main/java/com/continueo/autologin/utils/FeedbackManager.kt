package com.continueo.autologin.utils

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

enum class FeedbackCategory(val label: String, val subjectPrefix: String) {
    BUG("Bug Report", "[Bug]"),
    COMPLAINT("Complaint", "[Complaint]"),
    FEATURE("Feature Request", "[Feature]"),
    GENERAL("General Feedback", "[Feedback]")
}

object FeedbackManager {

    const val WEB3FORMS_SUBMIT_URL = "https://api.web3forms.com/submit"
    const val FEEDBACK_WEB_URL = "https://bmsit-cp.vercel.app/feedback"

    /**
     * Public Web3Forms Access Key.
     * Can also be updated remotely via the `web3formsAccessKey` field in version.json.
     */
    const val DEFAULT_WEB3FORMS_ACCESS_KEY = "605e48ce-89a3-44c9-b19c-c2fe53896441"

    private const val VERSION_JSON_URL =
        "https://raw.githubusercontent.com/adityasing9/BMSIT-CP/main/version.json"

    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun isValidEmail(value: String): Boolean {
        return EMAIL_REGEX.matches(value.trim())
    }

    /**
     * Builds the payload fields sent to Web3Forms.
     * Never includes student credentials (USN, DOB, ID card number).
     */
    fun buildPayloadFields(
        accessKey: String,
        category: FeedbackCategory,
        subject: String,
        message: String,
        contact: String,
        appVersion: String,
        androidVersion: String,
        deviceModel: String,
        includeDeviceInfo: Boolean
    ): Map<String, String> {
        val cleanSubject = subject.trim().ifBlank { category.label }
        val cleanContact = contact.trim()

        return buildMap {
            put("access_key", accessKey.trim())
            put("from_name", "BMSIT CP Android")
            put("subject", "BMSIT CP ${category.subjectPrefix}: $cleanSubject")
            put("category", category.label)
            put("message", message.trim())

            if (cleanContact.isNotEmpty()) {
                if (isValidEmail(cleanContact)) {
                    put("email", cleanContact)
                    put("replyto", cleanContact)
                } else {
                    put("contact", cleanContact)
                }
            } else {
                put("contact", "Anonymous")
            }

            if (includeDeviceInfo) {
                put("app_version", "v$appVersion")
                put("android_version", androidVersion)
                put("device", deviceModel)
            }
        }
    }

    fun buildPayloadJson(
        accessKey: String,
        category: FeedbackCategory,
        subject: String,
        message: String,
        contact: String,
        appVersion: String,
        androidVersion: String,
        deviceModel: String,
        includeDeviceInfo: Boolean
    ): JSONObject {
        val fields = buildPayloadFields(
            accessKey = accessKey,
            category = category,
            subject = subject,
            message = message,
            contact = contact,
            appVersion = appVersion,
            androidVersion = androidVersion,
            deviceModel = deviceModel,
            includeDeviceInfo = includeDeviceInfo
        )
        return JSONObject(fields)
    }

    private fun resolveRemoteAccessKey(): String {
        return try {
            val timestamp = System.currentTimeMillis()
            val conn = (URL("$VERSION_JSON_URL?_t=$timestamp").openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                useCaches = false
                requestMethod = "GET"
                setRequestProperty("User-Agent", "BMSIT-CP-Android")
                setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
            }
            if (conn.responseCode in 200..299) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                JSONObject(text).optString("web3formsAccessKey", "").trim()
            } else {
                ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    suspend fun submitFeedback(
        context: Context,
        category: FeedbackCategory,
        subject: String,
        message: String,
        contact: String,
        includeDeviceInfo: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        val trimmedMessage = message.trim()
        if (trimmedMessage.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Please enter your feedback or complaint details."))
        }

        val accessKey = DEFAULT_WEB3FORMS_ACCESS_KEY.trim().ifBlank { resolveRemoteAccessKey() }
        if (accessKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Web3Forms access key is not configured yet. Please try the web feedback form.")
            )
        }

        val appVersion = UpdateManager.getCurrentVersionName(context)
        val androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

        val payload = buildPayloadJson(
            accessKey = accessKey,
            category = category,
            subject = subject,
            message = trimmedMessage,
            contact = contact,
            appVersion = appVersion,
            androidVersion = androidVersion,
            deviceModel = deviceModel,
            includeDeviceInfo = includeDeviceInfo
        )

        try {
            val conn = (URL(WEB3FORMS_SUBMIT_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 15000
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Origin", "https://bmsit-cp.vercel.app")
                setRequestProperty("Referer", FEEDBACK_WEB_URL)
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}) BMSIT-CP/$appVersion"
                )
            }

            conn.outputStream.use { os ->
                val bytes = payload.toString().toByteArray(Charsets.UTF_8)
                os.write(bytes, 0, bytes.size)
                os.flush()
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (code in 200..299) {
                val json = try { JSONObject(body) } catch (_: Exception) { null }
                val success = json?.optBoolean("success", true) ?: true
                val serverMessage = json?.optString("message", "").orEmpty()
                if (success) {
                    Result.success(serverMessage.ifBlank { "Thank you! Your feedback has been sent." })
                } else {
                    Result.failure(Exception(serverMessage.ifBlank { "Submission rejected by server." }))
                }
            } else {
                val json = try { JSONObject(body) } catch (_: Exception) { null }
                val serverError = json?.optString("message", "").orEmpty()
                Result.failure(Exception(serverError.ifBlank { "Server returned HTTP $code" }))
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Network error while sending feedback"))
        }
    }
}
