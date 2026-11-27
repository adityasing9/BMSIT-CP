package com.continueo.autologin.notifications

import android.content.Context

class NotificationPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) { prefs.edit().putBoolean(KEY_ENABLED, value).apply() }

    var onlyBelowThreshold: Boolean
        get() = prefs.getBoolean(KEY_ONLY_BELOW_THRESHOLD, false)
        set(value) { prefs.edit().putBoolean(KEY_ONLY_BELOW_THRESHOLD, value).apply() }

    var thresholdPercent: Int
        get() = prefs.getInt(KEY_THRESHOLD_PERCENT, DEFAULT_THRESHOLD).coerceIn(50, 95)
        set(value) { prefs.edit().putInt(KEY_THRESHOLD_PERCENT, value.coerceIn(50, 95)).apply() }

    var hasPromptedPermission: Boolean
        get() = prefs.getBoolean(KEY_PROMPTED_PERMISSION, false)
        set(value) { prefs.edit().putBoolean(KEY_PROMPTED_PERMISSION, value).apply() }

    companion object {
        private const val PREFS_NAME = "bmsit_cp_notification_prefs"
        private const val KEY_ENABLED = "notifications_enabled"
        private const val KEY_ONLY_BELOW_THRESHOLD = "only_below_threshold"
        private const val KEY_THRESHOLD_PERCENT = "threshold_percent"
        private const val KEY_PROMPTED_PERMISSION = "has_prompted_permission"
        const val DEFAULT_THRESHOLD = 85
    }
}
