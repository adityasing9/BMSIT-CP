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

    var remoteAnnouncementsEnabled: Boolean
        get() = prefs.getBoolean(KEY_REMOTE_ANNOUNCEMENTS, true)
        set(value) { prefs.edit().putBoolean(KEY_REMOTE_ANNOUNCEMENTS, value).apply() }

    var backgroundUpdateAlertsEnabled: Boolean
        get() = prefs.getBoolean(KEY_BG_UPDATE_ALERTS, true)
        set(value) { prefs.edit().putBoolean(KEY_BG_UPDATE_ALERTS, value).apply() }

    var dailyReminderEnabled: Boolean
        get() = prefs.getBoolean(KEY_DAILY_REMINDER_ENABLED, true)
        set(value) { prefs.edit().putBoolean(KEY_DAILY_REMINDER_ENABLED, value).apply() }

    var reminderHour: Int
        get() = prefs.getInt(KEY_REMINDER_HOUR, DEFAULT_REMINDER_HOUR).coerceIn(0, 23)
        set(value) { prefs.edit().putInt(KEY_REMINDER_HOUR, value.coerceIn(0, 23)).apply() }

    var reminderMinute: Int
        get() = prefs.getInt(KEY_REMINDER_MINUTE, DEFAULT_REMINDER_MINUTE).coerceIn(0, 59)
        set(value) { prefs.edit().putInt(KEY_REMINDER_MINUTE, value.coerceIn(0, 59)).apply() }

    var lastSeenAnnouncementId: String
        get() = prefs.getString(KEY_LAST_ANNOUNCEMENT_ID, "") ?: ""
        set(value) { prefs.edit().putString(KEY_LAST_ANNOUNCEMENT_ID, value).apply() }

    var lastNotifiedUpdateVersion: String
        get() = prefs.getString(KEY_LAST_UPDATE_VERSION, "") ?: ""
        set(value) { prefs.edit().putString(KEY_LAST_UPDATE_VERSION, value).apply() }

    var lastReminderDayKey: String
        get() = prefs.getString(KEY_LAST_REMINDER_DAY, "") ?: ""
        set(value) { prefs.edit().putString(KEY_LAST_REMINDER_DAY, value).apply() }

    companion object {
        private const val PREFS_NAME = "bmsit_cp_notification_prefs"
        private const val KEY_ENABLED = "notifications_enabled"
        private const val KEY_ONLY_BELOW_THRESHOLD = "only_below_threshold"
        private const val KEY_THRESHOLD_PERCENT = "threshold_percent"
        private const val KEY_PROMPTED_PERMISSION = "has_prompted_permission"
        private const val KEY_REMOTE_ANNOUNCEMENTS = "remote_announcements_enabled"
        private const val KEY_BG_UPDATE_ALERTS = "background_update_alerts_enabled"
        private const val KEY_DAILY_REMINDER_ENABLED = "daily_reminder_enabled"
        private const val KEY_REMINDER_HOUR = "daily_reminder_hour"
        private const val KEY_REMINDER_MINUTE = "daily_reminder_minute"
        private const val KEY_LAST_ANNOUNCEMENT_ID = "last_seen_announcement_id"
        private const val KEY_LAST_UPDATE_VERSION = "last_notified_update_version"
        private const val KEY_LAST_REMINDER_DAY = "last_reminder_day_key"

        const val DEFAULT_THRESHOLD = 85
        const val DEFAULT_REMINDER_HOUR = 18
        const val DEFAULT_REMINDER_MINUTE = 0
    }
}
