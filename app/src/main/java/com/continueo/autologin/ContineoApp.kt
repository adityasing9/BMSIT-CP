package com.continueo.autologin

import android.app.Application
import com.continueo.autologin.notifications.AttendanceNotificationManager
import com.continueo.autologin.notifications.NotificationScheduler

class ContineoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AttendanceNotificationManager.ensureChannels(this)
        NotificationScheduler.syncAllSchedules(this)
    }
}
