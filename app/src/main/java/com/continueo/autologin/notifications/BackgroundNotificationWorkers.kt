package com.continueo.autologin.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.continueo.autologin.security.AttendanceSnapshotStore
import com.continueo.autologin.security.CredentialManager
import com.continueo.autologin.utils.AttendanceUiHelper
import com.continueo.autologin.utils.UpdateManager
import java.util.concurrent.TimeUnit

class RemoteSyncCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = NotificationPreferences(applicationContext)
        if (!prefs.isEnabled || !AttendanceNotificationManager.hasPermission(applicationContext)) {
            return Result.success()
        }
        if (!prefs.remoteAnnouncementsEnabled && !prefs.backgroundUpdateAlertsEnabled) {
            return Result.success()
        }

        val configResult = UpdateManager.fetchVersionJsonSnapshot(applicationContext)
        configResult.onSuccess { snapshot ->
            val currentVersionCode = UpdateManager.getCurrentVersionCode(applicationContext)
            val announcement = snapshot.announcement
            if (prefs.remoteAnnouncementsEnabled &&
                announcement != null &&
                announcement.shouldDeliver(currentVersionCode, prefs.lastSeenAnnouncementId)
            ) {
                val posted = AttendanceNotificationManager.notifyAnnouncement(applicationContext, announcement)
                if (posted) {
                    prefs.lastSeenAnnouncementId = announcement.id
                }
            }

            val updateInfo = snapshot.updateInfo
            if (prefs.backgroundUpdateAlertsEnabled &&
                updateInfo != null &&
                updateInfo.isUpdateAvailable &&
                updateInfo.latestVersionName != prefs.lastNotifiedUpdateVersion
            ) {
                val posted = AttendanceNotificationManager.notifyUpdateAvailable(applicationContext, updateInfo)
                if (posted) {
                    prefs.lastNotifiedUpdateVersion = updateInfo.latestVersionName
                }
            }
        }
        return Result.success()
    }
}

class DailySyncReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = NotificationPreferences(applicationContext)
        if (!prefs.isEnabled || !prefs.dailyReminderEnabled) {
            return Result.success()
        }
        if (!AttendanceNotificationManager.hasPermission(applicationContext)) {
            return Result.success()
        }

        try {
            val credentialManager = CredentialManager(applicationContext)
            val accounts = credentialManager.getAllAccounts()
            val activeAccount = credentialManager.getActiveAccount()
            val latestSnapshot = activeAccount?.let {
                AttendanceSnapshotStore(applicationContext).getSnapshot(it.id)
            }
            val nowMillis = System.currentTimeMillis()
            val shouldNotify = AttendanceUiHelper.shouldSendDailySyncReminder(
                hasAccount = accounts.isNotEmpty(),
                snapshotUpdatedAtMillis = latestSnapshot?.updatedAtMillis ?: 0L,
                nowMillis = nowMillis,
                lastReminderDayKey = prefs.lastReminderDayKey
            )
            if (shouldNotify) {
                val posted = AttendanceNotificationManager.notifyDailySyncReminder(
                    context = applicationContext,
                    activeAccount = activeAccount,
                    accounts = accounts
                )
                if (posted) {
                    prefs.lastReminderDayKey = AttendanceUiHelper.toLocalDayKey(nowMillis)
                }
            }
        } catch (_: Exception) {
            // Keep background worker resilient even if keystore is temporarily locked
        }

        return Result.success()
    }
}

object NotificationScheduler {

    private const val WORK_REMOTE_CHECK = "bmsit_cp_remote_sync_check"
    private const val WORK_DAILY_REMINDER = "bmsit_cp_daily_sync_reminder"

    fun syncAllSchedules(context: Context, replaceReminderSchedule: Boolean = false) {
        val appContext = context.applicationContext
        val prefs = NotificationPreferences(appContext)
        val workManager = try {
            WorkManager.getInstance(appContext)
        } catch (_: Exception) {
            return
        }

        val shouldRunRemoteCheck = prefs.isEnabled &&
            (prefs.remoteAnnouncementsEnabled || prefs.backgroundUpdateAlertsEnabled)

        if (shouldRunRemoteCheck) {
            val remoteConstraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val remoteRequest = PeriodicWorkRequestBuilder<RemoteSyncCheckWorker>(
                6,
                TimeUnit.HOURS
            )
                .setConstraints(remoteConstraints)
                .build()
            workManager.enqueueUniquePeriodicWork(
                WORK_REMOTE_CHECK,
                ExistingPeriodicWorkPolicy.KEEP,
                remoteRequest
            )
        } else {
            workManager.cancelUniqueWork(WORK_REMOTE_CHECK)
        }

        val shouldRunDailyReminder = prefs.isEnabled && prefs.dailyReminderEnabled
        if (shouldRunDailyReminder) {
            val initialDelay = AttendanceUiHelper.computeInitialDelayMillis(
                nowMillis = System.currentTimeMillis(),
                targetHour = prefs.reminderHour,
                targetMinute = prefs.reminderMinute
            )
            val reminderRequest = PeriodicWorkRequestBuilder<DailySyncReminderWorker>(
                24,
                TimeUnit.HOURS
            )
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .build()
            workManager.enqueueUniquePeriodicWork(
                WORK_DAILY_REMINDER,
                if (replaceReminderSchedule) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
                reminderRequest
            )
        } else {
            workManager.cancelUniqueWork(WORK_DAILY_REMINDER)
        }
    }
}
