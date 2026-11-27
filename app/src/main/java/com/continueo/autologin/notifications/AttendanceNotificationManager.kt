package com.continueo.autologin.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.continueo.autologin.MainActivity
import com.continueo.autologin.R
import com.continueo.autologin.automation.ContineoSelectors
import com.continueo.autologin.models.AccountProfile
import com.continueo.autologin.models.AttendanceSnapshot
import com.continueo.autologin.models.AttendanceStatus
import com.continueo.autologin.utils.AttendanceAlertSummary
import com.continueo.autologin.utils.AttendanceUiHelper
import com.continueo.autologin.utils.RemoteAnnouncement
import com.continueo.autologin.utils.SubjectGridSpec
import com.continueo.autologin.utils.UpdateInfo
import com.continueo.autologin.utils.UpdateManager
import com.continueo.autologin.widget.AttendanceWidgetProvider

object AttendanceNotificationManager {

    const val CHANNEL_SYNC_ID = "attendance_sync_updates"
    const val CHANNEL_ALERT_ID = "attendance_shortage_alerts"
    const val CHANNEL_ANNOUNCEMENT_ID = "remote_announcements"
    const val CHANNEL_REMINDER_ID = "daily_sync_reminders"

    const val NOTIFICATION_ID = 4201
    const val NOTIFICATION_ANNOUNCEMENT_ID = 4202
    const val NOTIFICATION_UPDATE_ID = 4203
    const val NOTIFICATION_REMINDER_ID = 4204

    private const val REQUEST_NOTIF_OPEN_APP = 4101
    private const val REQUEST_NOTIF_SYNC = 4102
    private const val REQUEST_NOTIF_SWITCH = 4103
    private const val REQUEST_NOTIF_PORTAL = 4104
    private const val REQUEST_NOTIF_ANNOUNCEMENT = 4105
    private const val REQUEST_NOTIF_UPDATE = 4106
    private const val REQUEST_NOTIF_REMINDER = 4107

    private val GRID_SPEC = SubjectGridSpec(
        rowIds = intArrayOf(
            R.id.notif_grid_row_0,
            R.id.notif_grid_row_1,
            R.id.notif_grid_row_2
        ),
        cellIds = intArrayOf(
            R.id.notif_cell_0,
            R.id.notif_cell_1,
            R.id.notif_cell_2,
            R.id.notif_cell_3,
            R.id.notif_cell_4,
            R.id.notif_cell_5
        ),
        nameIds = intArrayOf(
            R.id.notif_cell_0_name,
            R.id.notif_cell_1_name,
            R.id.notif_cell_2_name,
            R.id.notif_cell_3_name,
            R.id.notif_cell_4_name,
            R.id.notif_cell_5_name
        ),
        pctIds = intArrayOf(
            R.id.notif_cell_0_pct,
            R.id.notif_cell_1_pct,
            R.id.notif_cell_2_pct,
            R.id.notif_cell_3_pct,
            R.id.notif_cell_4_pct,
            R.id.notif_cell_5_pct
        ),
        safeBarIds = intArrayOf(
            R.id.notif_cell_0_progress_safe,
            R.id.notif_cell_1_progress_safe,
            R.id.notif_cell_2_progress_safe,
            R.id.notif_cell_3_progress_safe,
            R.id.notif_cell_4_progress_safe,
            R.id.notif_cell_5_progress_safe
        ),
        warnBarIds = intArrayOf(
            R.id.notif_cell_0_progress_warn,
            R.id.notif_cell_1_progress_warn,
            R.id.notif_cell_2_progress_warn,
            R.id.notif_cell_3_progress_warn,
            R.id.notif_cell_4_progress_warn,
            R.id.notif_cell_5_progress_warn
        ),
        dangerBarIds = intArrayOf(
            R.id.notif_cell_0_progress_danger,
            R.id.notif_cell_1_progress_danger,
            R.id.notif_cell_2_progress_danger,
            R.id.notif_cell_3_progress_danger,
            R.id.notif_cell_4_progress_danger,
            R.id.notif_cell_5_progress_danger
        )
    )

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val syncChannel = NotificationChannel(
            CHANNEL_SYNC_ID,
            context.getString(R.string.notif_channel_sync_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notif_channel_sync_desc)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }

        val alertChannel = NotificationChannel(
            CHANNEL_ALERT_ID,
            context.getString(R.string.notif_channel_alert_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_alert_desc)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }

        val announceChannel = NotificationChannel(
            CHANNEL_ANNOUNCEMENT_ID,
            context.getString(R.string.notif_channel_announce_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_announce_desc)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val reminderChannel = NotificationChannel(
            CHANNEL_REMINDER_ID,
            context.getString(R.string.notif_channel_reminder_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notif_channel_reminder_desc)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }

        manager.createNotificationChannels(
            listOf(syncChannel, alertChannel, announceChannel, reminderChannel)
        )
    }

    fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun cancelAttendanceNotification(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        } catch (_: Exception) {
            // Ignore
        }
    }

    @SuppressLint("MissingPermission")
    fun notifyFromSync(
        context: Context,
        snapshot: AttendanceSnapshot,
        activeAccount: AccountProfile?,
        accounts: List<AccountProfile>,
        forceShow: Boolean = false,
        silentUpdate: Boolean = false
    ): Boolean {
        val prefs = NotificationPreferences(context)
        if (!forceShow && !prefs.isEnabled) return false
        if (!hasPermission(context)) return false
        if (!AttendanceUiHelper.shouldPostNotification(
                snapshot = snapshot,
                onlyBelowThreshold = if (forceShow) false else prefs.onlyBelowThreshold,
                thresholdPercent = prefs.thresholdPercent
            )
        ) {
            return false
        }

        ensureChannels(context)
        val summary = AttendanceUiHelper.buildAlertSummary(snapshot, prefs.thresholdPercent)
        val safeAccountName = AttendanceUiHelper.formatSafeAccountName(activeAccount, accounts)
        val syncTime = AttendanceUiHelper.formatSyncTime(snapshot.updatedAtMillis)

        val channelId = if (summary.belowThresholdCount > 0 || summary.severity == AttendanceStatus.DANGER) {
            CHANNEL_ALERT_ID
        } else {
            CHANNEL_SYNC_ID
        }

        val collapsedViews = buildCollapsedViews(context, summary, safeAccountName)
        val expandedViews = buildExpandedViews(context, summary, safeAccountName, syncTime)
        val publicNotification = buildPublicLockScreenNotification(context, channelId, summary.belowThresholdCount > 0)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_attendance)
            .setColor(ContextCompat.getColor(context, AttendanceUiHelper.statusColorRes(summary.severity)))
            .setContentTitle(summary.headline)
            .setContentText("$safeAccountName • ${summary.subline}")
            .setSubText(safeAccountName)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(collapsedViews)
            .setCustomBigContentView(expandedViews)
            .setPublicVersion(publicNotification)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(
                if (channelId == CHANNEL_ALERT_ID) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT
            )
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(createOpenAppPendingIntent(context))
            .setOngoing(false)
            .setAutoCancel(true)
            .setOnlyAlertOnce(silentUpdate)
            .apply {
                addStandardActions(context, this, accounts.size > 1)
            }
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun notifyAccountSwitchedNoData(
        context: Context,
        activeAccount: AccountProfile?,
        accounts: List<AccountProfile>
    ): Boolean {
        if (!hasPermission(context)) return false
        ensureChannels(context)
        val safeName = AttendanceUiHelper.formatSafeAccountName(activeAccount, accounts)
        val publicNotification = buildPublicLockScreenNotification(context, CHANNEL_SYNC_ID, hasAlert = false)

        val notification = NotificationCompat.Builder(context, CHANNEL_SYNC_ID)
            .setSmallIcon(R.drawable.ic_stat_attendance)
            .setColor(ContextCompat.getColor(context, R.color.primary))
            .setContentTitle("Switched to $safeName")
            .setContentText("Not synced yet — tap Sync Now to load attendance")
            .setPublicVersion(publicNotification)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(createOpenAppPendingIntent(context))
            .setOngoing(false)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .apply {
                addStandardActions(context, this, accounts.size > 1)
            }
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun notifyAnnouncement(
        context: Context,
        announcement: RemoteAnnouncement
    ): Boolean {
        if (!hasPermission(context)) return false
        ensureChannels(context)

        val title = announcement.title.ifBlank { "BMSIT CP Notice" }
        val body = announcement.message.ifBlank { title }
        val safeUrl = announcement.sanitizedActionUrl()
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val contentPendingIntent = if (safeUrl.isNotBlank()) {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            PendingIntent.getActivity(context, REQUEST_NOTIF_ANNOUNCEMENT, browserIntent, pendingFlags)
        } else {
            createOpenAppPendingIntent(context)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ANNOUNCEMENT_ID)
            .setSmallIcon(R.drawable.ic_stat_attendance)
            .setColor(ContextCompat.getColor(context, R.color.primary))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentPendingIntent)
            .setOngoing(false)
            .setAutoCancel(true)

        if (safeUrl.isNotBlank()) {
            builder.addAction(0, announcement.actionLabel, contentPendingIntent)
        }

        return try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ANNOUNCEMENT_ID, builder.build())
            true
        } catch (_: SecurityException) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun notifyUpdateAvailable(
        context: Context,
        updateInfo: UpdateInfo
    ): Boolean {
        if (!hasPermission(context)) return false
        ensureChannels(context)

        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val downloadUrl = updateInfo.downloadUrl.ifBlank { UpdateManager.DIRECT_APK_URL }
        val downloadIntent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val downloadPendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_NOTIF_UPDATE,
            downloadIntent,
            pendingFlags
        )

        val body = updateInfo.releaseNotes.trim().ifBlank {
            "New release BMSIT CP v${updateInfo.latestVersionName} is ready to download."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ANNOUNCEMENT_ID)
            .setSmallIcon(R.drawable.ic_stat_attendance)
            .setColor(ContextCompat.getColor(context, R.color.primary))
            .setContentTitle("Update Available: v${updateInfo.latestVersionName}")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(createOpenAppPendingIntent(context))
            .setOngoing(false)
            .setAutoCancel(true)
            .addAction(0, "Download APK", downloadPendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_UPDATE_ID, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun notifyDailySyncReminder(
        context: Context,
        activeAccount: AccountProfile?,
        accounts: List<AccountProfile>
    ): Boolean {
        if (!hasPermission(context)) return false
        ensureChannels(context)

        val safeAccountName = AttendanceUiHelper.formatSafeAccountName(activeAccount, accounts)
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val syncIntent = Intent(context, MainActivity::class.java).apply {
            action = AttendanceWidgetProvider.ACTION_WIDGET_LOGIN
            putExtra(AttendanceWidgetProvider.EXTRA_WIDGET_ACTION, AttendanceWidgetProvider.ACTION_WIDGET_LOGIN)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val syncPendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_NOTIF_REMINDER,
            syncIntent,
            pendingFlags
        )

        val publicNotification = NotificationCompat.Builder(context, CHANNEL_REMINDER_ID)
            .setSmallIcon(R.drawable.ic_stat_attendance)
            .setColor(ContextCompat.getColor(context, R.color.primary))
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.notif_public_reminder_body))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        val text = "Tap Sync Now to update $safeAccountName's attendance & home widget."
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER_ID)
            .setSmallIcon(R.drawable.ic_stat_attendance)
            .setColor(ContextCompat.getColor(context, R.color.primary))
            .setContentTitle("Haven't synced today's attendance")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPublicVersion(publicNotification)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(syncPendingIntent)
            .setOngoing(false)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.notif_action_sync), syncPendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_REMINDER_ID, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun buildCollapsedViews(
        context: Context,
        summary: AttendanceAlertSummary,
        safeAccountName: String
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.notification_attendance_collapsed)
        views.setImageViewBitmap(
            R.id.notif_collapsed_ring,
            AttendanceUiHelper.drawAttendanceRingBitmap(
                context = context,
                overallPercentage = summary.overallPercentage,
                sizePx = 112,
                strokePx = 11f,
                textColorRes = R.color.notif_text_primary,
                trackColorRes = R.color.widget_track
            )
        )
        views.setTextViewText(R.id.notif_collapsed_title, summary.headline)
        views.setTextViewText(R.id.notif_collapsed_subtitle, "$safeAccountName • ${summary.subline}")

        bindStatusPill(
            views = views,
            safeId = R.id.notif_collapsed_badge_safe,
            warnId = R.id.notif_collapsed_badge_warn,
            dangerId = R.id.notif_collapsed_badge_danger,
            severity = summary.severity,
            badgeText = summary.badgeText
        )
        return views
    }

    private fun buildExpandedViews(
        context: Context,
        summary: AttendanceAlertSummary,
        safeAccountName: String,
        syncTime: String
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.notification_attendance_expanded)
        views.setImageViewBitmap(
            R.id.notif_exp_ring,
            AttendanceUiHelper.drawAttendanceRingBitmap(
                context = context,
                overallPercentage = summary.overallPercentage,
                sizePx = 128,
                strokePx = 12f,
                textColorRes = R.color.notif_text_primary,
                trackColorRes = R.color.widget_track
            )
        )
        val expTitle = if (summary.belowThresholdCount > 0) "Low Attendance Alert" else "Attendance Overview"
        views.setTextViewText(R.id.notif_exp_title, expTitle)
        views.setTextViewText(R.id.notif_exp_subtitle, "$safeAccountName • ${summary.headline} • $syncTime")

        bindStatusPill(
            views = views,
            safeId = R.id.notif_exp_badge_safe,
            warnId = R.id.notif_exp_badge_warn,
            dangerId = R.id.notif_exp_badge_danger,
            severity = summary.severity,
            badgeText = summary.badgeText
        )

        if (summary.sortedSubjects.isEmpty()) {
            views.setViewVisibility(R.id.notif_subjects_container, View.GONE)
        } else {
            views.setViewVisibility(R.id.notif_subjects_container, View.VISIBLE)
            val shown = AttendanceUiHelper.bindSubjectsGrid(
                context = context,
                views = views,
                spec = GRID_SPEC,
                subjects = summary.sortedSubjects,
                maxVisibleCells = GRID_SPEC.cellIds.size
            )
            if (summary.sortedSubjects.size > shown) {
                views.setViewVisibility(R.id.notif_footer_text, View.VISIBLE)
                views.setTextViewText(
                    R.id.notif_footer_text,
                    "+${summary.sortedSubjects.size - shown} more subjects • Lowest shown first"
                )
            } else {
                views.setViewVisibility(R.id.notif_footer_text, View.GONE)
            }
        }
        return views
    }

    private fun bindStatusPill(
        views: RemoteViews,
        safeId: Int,
        warnId: Int,
        dangerId: Int,
        severity: AttendanceStatus,
        badgeText: String
    ) {
        when (severity) {
            AttendanceStatus.SAFE -> {
                views.setViewVisibility(safeId, View.VISIBLE)
                views.setTextViewText(safeId, badgeText)
                views.setViewVisibility(warnId, View.GONE)
                views.setViewVisibility(dangerId, View.GONE)
            }
            AttendanceStatus.WARNING -> {
                views.setViewVisibility(safeId, View.GONE)
                views.setViewVisibility(warnId, View.VISIBLE)
                views.setTextViewText(warnId, badgeText)
                views.setViewVisibility(dangerId, View.GONE)
            }
            AttendanceStatus.DANGER -> {
                views.setViewVisibility(safeId, View.GONE)
                views.setViewVisibility(warnId, View.GONE)
                views.setViewVisibility(dangerId, View.VISIBLE)
                views.setTextViewText(dangerId, badgeText)
            }
        }
    }

    private fun buildPublicLockScreenNotification(
        context: Context,
        channelId: String,
        hasAlert: Boolean
    ): Notification {
        val publicText = if (hasAlert) {
            context.getString(R.string.notif_public_alert_body)
        } else {
            context.getString(R.string.notif_public_sync_body)
        }
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_attendance)
            .setColor(ContextCompat.getColor(context, R.color.primary))
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(publicText)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun addStandardActions(
        context: Context,
        builder: NotificationCompat.Builder,
        canSwitchAccounts: Boolean
    ) {
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val syncIntent = Intent(context, MainActivity::class.java).apply {
            action = AttendanceWidgetProvider.ACTION_WIDGET_LOGIN
            putExtra(AttendanceWidgetProvider.EXTRA_WIDGET_ACTION, AttendanceWidgetProvider.ACTION_WIDGET_LOGIN)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val syncPendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_NOTIF_SYNC,
            syncIntent,
            pendingFlags
        )
        builder.addAction(0, context.getString(R.string.notif_action_sync), syncPendingIntent)

        if (canSwitchAccounts) {
            val switchIntent = Intent(context, AttendanceNotificationReceiver::class.java).apply {
                action = AttendanceNotificationReceiver.ACTION_SWITCH_ACCOUNT
            }
            val switchPendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_NOTIF_SWITCH,
                switchIntent,
                pendingFlags
            )
            builder.addAction(0, context.getString(R.string.widget_action_switch), switchPendingIntent)
        }

        val portalIntent = Intent(Intent.ACTION_VIEW, Uri.parse(ContineoSelectors.PORTAL_URL)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val portalPendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_NOTIF_PORTAL,
            portalIntent,
            pendingFlags
        )
        builder.addAction(0, context.getString(R.string.widget_action_portal), portalPendingIntent)
    }

    private fun createOpenAppPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_NOTIF_OPEN_APP,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
