package com.continueo.autologin.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.continueo.autologin.MainActivity
import com.continueo.autologin.R
import com.continueo.autologin.automation.ContineoSelectors
import com.continueo.autologin.models.AttendanceSnapshot
import com.continueo.autologin.models.AttendanceStatus
import com.continueo.autologin.models.SubjectAttendance
import com.continueo.autologin.security.AttendanceSnapshotStore
import com.continueo.autologin.security.CredentialManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AttendanceWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateWidgets(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_SWITCH_ACCOUNT) {
            cycleToNextAccount(context)
        }
    }

    private fun cycleToNextAccount(context: Context) {
        try {
            val credentialManager = CredentialManager(context)
            val accounts = credentialManager.getAllAccounts()
            if (accounts.size > 1) {
                val activeId = credentialManager.getActiveAccountId()
                val currentIdx = accounts.indexOfFirst { it.id == activeId }
                val nextIdx = if (currentIdx >= 0) (currentIdx + 1) % accounts.size else 0
                credentialManager.setActiveAccountId(accounts[nextIdx].id)
            }
        } catch (_: Exception) {
            // Keep widget resilient even if keystore is temporarily locked
        }
        refreshAllWidgets(context)
    }

    companion object {
        const val ACTION_SWITCH_ACCOUNT = "com.continueo.autologin.widget.ACTION_SWITCH_ACCOUNT"
        const val ACTION_WIDGET_LOGIN = "com.continueo.autologin.widget.ACTION_WIDGET_LOGIN"
        const val EXTRA_WIDGET_ACTION = "extra_widget_action"

        private const val REQUEST_OPEN_APP = 3001
        private const val REQUEST_LOGIN_SYNC = 3002
        private const val REQUEST_SWITCH_ACCOUNT = 3003
        private const val REQUEST_OPEN_PORTAL = 3004

        private val CELL_IDS = intArrayOf(
            R.id.widget_cell_0,
            R.id.widget_cell_1,
            R.id.widget_cell_2,
            R.id.widget_cell_3,
            R.id.widget_cell_4,
            R.id.widget_cell_5
        )
        private val ROW_IDS = intArrayOf(
            R.id.widget_grid_row_0,
            R.id.widget_grid_row_1,
            R.id.widget_grid_row_2
        )
        private val NAME_IDS = intArrayOf(
            R.id.widget_cell_0_name,
            R.id.widget_cell_1_name,
            R.id.widget_cell_2_name,
            R.id.widget_cell_3_name,
            R.id.widget_cell_4_name,
            R.id.widget_cell_5_name
        )
        private val PCT_IDS = intArrayOf(
            R.id.widget_cell_0_pct,
            R.id.widget_cell_1_pct,
            R.id.widget_cell_2_pct,
            R.id.widget_cell_3_pct,
            R.id.widget_cell_4_pct,
            R.id.widget_cell_5_pct
        )
        private val SAFE_BAR_IDS = intArrayOf(
            R.id.widget_cell_0_progress_safe,
            R.id.widget_cell_1_progress_safe,
            R.id.widget_cell_2_progress_safe,
            R.id.widget_cell_3_progress_safe,
            R.id.widget_cell_4_progress_safe,
            R.id.widget_cell_5_progress_safe
        )
        private val WARN_BAR_IDS = intArrayOf(
            R.id.widget_cell_0_progress_warn,
            R.id.widget_cell_1_progress_warn,
            R.id.widget_cell_2_progress_warn,
            R.id.widget_cell_3_progress_warn,
            R.id.widget_cell_4_progress_warn,
            R.id.widget_cell_5_progress_warn
        )
        private val DANGER_BAR_IDS = intArrayOf(
            R.id.widget_cell_0_progress_danger,
            R.id.widget_cell_1_progress_danger,
            R.id.widget_cell_2_progress_danger,
            R.id.widget_cell_3_progress_danger,
            R.id.widget_cell_4_progress_danger,
            R.id.widget_cell_5_progress_danger
        )

        fun refreshAllWidgets(context: Context) {
            try {
                val appContext = context.applicationContext
                val appWidgetManager = AppWidgetManager.getInstance(appContext)
                val componentName = ComponentName(appContext, AttendanceWidgetProvider::class.java)
                val ids = appWidgetManager.getAppWidgetIds(componentName)
                if (ids.isNotEmpty()) {
                    updateWidgets(appContext, appWidgetManager, ids)
                }
            } catch (_: Exception) {
                // Non-critical if launcher hasn't hosted widgets
            }
        }

        fun requestPinWidget(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
            return try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                if (!appWidgetManager.isRequestPinAppWidgetSupported) return false
                val provider = ComponentName(context, AttendanceWidgetProvider::class.java)
                appWidgetManager.requestPinAppWidget(provider, null, null)
            } catch (_: Exception) {
                false
            }
        }

        private fun updateWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            for (appWidgetId in appWidgetIds) {
                val views = buildRemoteViews(context)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }

        private fun buildRemoteViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_attendance)

            val accountState = try {
                val credentialManager = CredentialManager(context)
                val accounts = credentialManager.getAllAccounts()
                val active = credentialManager.getActiveAccount()
                Pair(accounts, active)
            } catch (_: Exception) {
                Pair(emptyList(), null)
            }

            val accounts = accountState.first
            val activeAccount = accountState.second
            val snapshot: AttendanceSnapshot? = activeAccount?.let {
                AttendanceSnapshotStore(context).getSnapshot(it.id)
            }

            val accountDisplayName = if (activeAccount == null) {
                "No account configured"
            } else {
                val rawLabel = activeAccount.getDisplayName().trim()
                val safeLabel = if (rawLabel.isBlank() || rawLabel.equals(activeAccount.credentials.usn.trim(), ignoreCase = true)) {
                    maskIdentifier(activeAccount.credentials.usn.trim())
                } else {
                    rawLabel
                }
                if (accounts.size > 1) {
                    val idx = accounts.indexOfFirst { it.id == activeAccount.id }.coerceAtLeast(0)
                    "$safeLabel (${idx + 1}/${accounts.size})"
                } else {
                    safeLabel
                }
            }

            val overallPct = snapshot?.displayOverallPercentage
            val subtitleText = when {
                activeAccount == null -> "Open app to set up"
                snapshot == null || !snapshot.hasData -> "Not synced yet"
                snapshot.subjects.isNotEmpty() -> "${formatSyncTime(snapshot.updatedAtMillis)} • ${snapshot.subjects.size} subj"
                else -> formatSyncTime(snapshot.updatedAtMillis)
            }

            views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_title_default))
            views.setTextViewText(R.id.widget_account_name, accountDisplayName)
            views.setTextViewText(R.id.widget_subtitle, subtitleText)
            views.setViewVisibility(
                R.id.widget_btn_switch,
                if (accounts.size > 1) View.VISIBLE else View.GONE
            )
            views.setImageViewBitmap(
                R.id.widget_ring_image,
                drawAttendanceRingBitmap(context, overallPct)
            )

            if (snapshot == null || !snapshot.hasData) {
                views.setViewVisibility(R.id.widget_empty_container, View.VISIBLE)
                views.setViewVisibility(R.id.widget_subjects_container, View.GONE)
                views.setTextViewText(
                    R.id.widget_empty_text,
                    if (activeAccount == null) {
                        "Set up your account in BMSIT CP, then tap Login & Sync to view attendance here."
                    } else {
                        "Tap Login & Sync once to fetch your latest attendance overview onto this widget."
                    }
                )
            } else {
                views.setViewVisibility(R.id.widget_empty_container, View.GONE)
                views.setViewVisibility(R.id.widget_subjects_container, View.VISIBLE)
                bindSubjectsGrid(context, views, snapshot)
            }

            bindClickActions(context, views)
            return views
        }

        private fun bindSubjectsGrid(
            context: Context,
            views: RemoteViews,
            snapshot: AttendanceSnapshot
        ) {
            val displaySubjects = snapshot.subjects.take(CELL_IDS.size)

            for (row in ROW_IDS.indices) {
                val firstCellIndex = row * 2
                if (firstCellIndex >= displaySubjects.size) {
                    views.setViewVisibility(ROW_IDS[row], View.GONE)
                    continue
                }
                views.setViewVisibility(ROW_IDS[row], View.VISIBLE)

                for (col in 0..1) {
                    val cellIndex = firstCellIndex + col
                    if (cellIndex >= displaySubjects.size) {
                        views.setViewVisibility(CELL_IDS[cellIndex], View.INVISIBLE)
                    } else {
                        views.setViewVisibility(CELL_IDS[cellIndex], View.VISIBLE)
                        bindSubjectCell(context, views, cellIndex, displaySubjects[cellIndex])
                    }
                }
            }

            when {
                snapshot.subjects.isEmpty() -> {
                    val pct = snapshot.displayOverallPercentage ?: 0
                    views.setViewVisibility(R.id.widget_more_text, View.VISIBLE)
                    views.setTextViewText(R.id.widget_more_text, "Overall attendance: $pct%")
                }
                snapshot.subjects.size > CELL_IDS.size -> {
                    views.setViewVisibility(R.id.widget_more_text, View.VISIBLE)
                    views.setTextViewText(
                        R.id.widget_more_text,
                        "+${snapshot.subjects.size - CELL_IDS.size} more subjects"
                    )
                }
                else -> {
                    views.setViewVisibility(R.id.widget_more_text, View.INVISIBLE)
                    views.setTextViewText(R.id.widget_more_text, "")
                }
            }
        }

        private fun bindSubjectCell(
            context: Context,
            views: RemoteViews,
            index: Int,
            subject: SubjectAttendance
        ) {
            val pct = subject.clampedPercentage
            views.setTextViewText(NAME_IDS[index], subject.name)
            views.setTextViewText(PCT_IDS[index], "$pct%")

            val statusColorRes = statusColorRes(subject.status)
            views.setTextColor(PCT_IDS[index], ContextCompat.getColor(context, statusColorRes))

            when (subject.status) {
                AttendanceStatus.SAFE -> {
                    views.setViewVisibility(SAFE_BAR_IDS[index], View.VISIBLE)
                    views.setProgressBar(SAFE_BAR_IDS[index], 100, pct, false)
                    views.setViewVisibility(WARN_BAR_IDS[index], View.GONE)
                    views.setViewVisibility(DANGER_BAR_IDS[index], View.GONE)
                }
                AttendanceStatus.WARNING -> {
                    views.setViewVisibility(SAFE_BAR_IDS[index], View.GONE)
                    views.setViewVisibility(WARN_BAR_IDS[index], View.VISIBLE)
                    views.setProgressBar(WARN_BAR_IDS[index], 100, pct, false)
                    views.setViewVisibility(DANGER_BAR_IDS[index], View.GONE)
                }
                AttendanceStatus.DANGER -> {
                    views.setViewVisibility(SAFE_BAR_IDS[index], View.GONE)
                    views.setViewVisibility(WARN_BAR_IDS[index], View.GONE)
                    views.setViewVisibility(DANGER_BAR_IDS[index], View.VISIBLE)
                    views.setProgressBar(DANGER_BAR_IDS[index], 100, pct, false)
                }
            }
        }

        private fun bindClickActions(context: Context, views: RemoteViews) {
            val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                REQUEST_OPEN_APP,
                openAppIntent,
                pendingFlags
            )
            views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent)

            val loginSyncIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_WIDGET_LOGIN
                putExtra(EXTRA_WIDGET_ACTION, ACTION_WIDGET_LOGIN)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val loginSyncPendingIntent = PendingIntent.getActivity(
                context,
                REQUEST_LOGIN_SYNC,
                loginSyncIntent,
                pendingFlags
            )
            views.setOnClickPendingIntent(R.id.widget_btn_sync, loginSyncPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_btn_login_empty, loginSyncPendingIntent)

            val switchIntent = Intent(context, AttendanceWidgetProvider::class.java).apply {
                action = ACTION_SWITCH_ACCOUNT
            }
            val switchPendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_SWITCH_ACCOUNT,
                switchIntent,
                pendingFlags
            )
            views.setOnClickPendingIntent(R.id.widget_btn_switch, switchPendingIntent)

            val portalIntent = Intent(Intent.ACTION_VIEW, Uri.parse(ContineoSelectors.PORTAL_URL)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val portalPendingIntent = PendingIntent.getActivity(
                context,
                REQUEST_OPEN_PORTAL,
                portalIntent,
                pendingFlags
            )
            views.setOnClickPendingIntent(R.id.widget_btn_portal, portalPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_btn_portal_active, portalPendingIntent)
        }

        private fun drawAttendanceRingBitmap(context: Context, overallPercentage: Int?): Bitmap {
            val sizePx = 144
            val strokePx = 13f
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val trackColor = ContextCompat.getColor(context, R.color.widget_track)
            val textColor = ContextCompat.getColor(context, R.color.widget_text_primary)
            val ringColorRes = when {
                overallPercentage == null -> R.color.widget_track
                overallPercentage >= 85 -> R.color.attendance_safe
                overallPercentage >= 75 -> R.color.attendance_warn
                else -> R.color.attendance_danger
            }
            val arcColor = ContextCompat.getColor(context, ringColorRes)

            val inset = strokePx / 2f + 4f
            val rect = RectF(inset, inset, sizePx - inset, sizePx - inset)

            val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = strokePx
                strokeCap = Paint.Cap.ROUND
                color = trackColor
            }
            canvas.drawArc(rect, 0f, 360f, false, trackPaint)

            if (overallPercentage != null) {
                val clamped = overallPercentage.coerceIn(0, 100)
                val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = strokePx
                    strokeCap = Paint.Cap.ROUND
                    color = arcColor
                }
                canvas.drawArc(rect, -90f, (clamped * 360f) / 100f, false, arcPaint)
            }

            val label = if (overallPercentage != null) "${overallPercentage.coerceIn(0, 100)}%" else "--"
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColor
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
                textSize = if (label.length >= 4) 30f else 34f
            }
            val fontMetrics = textPaint.fontMetrics
            val textY = sizePx / 2f - (fontMetrics.ascent + fontMetrics.descent) / 2f
            canvas.drawText(label, sizePx / 2f, textY, textPaint)

            return bitmap
        }

        private fun statusColorRes(status: AttendanceStatus): Int {
            return when (status) {
                AttendanceStatus.SAFE -> R.color.attendance_safe
                AttendanceStatus.WARNING -> R.color.attendance_warn
                AttendanceStatus.DANGER -> R.color.attendance_danger
            }
        }

        private fun maskIdentifier(raw: String): String {
            if (raw.length < 5) return "Account"
            return "${raw.take(2)}***${raw.takeLast(2)}"
        }

        private fun formatSyncTime(updatedAtMillis: Long): String {
            if (updatedAtMillis <= 0L) return "Synced"
            val diffMillis = (System.currentTimeMillis() - updatedAtMillis).coerceAtLeast(0L)
            val minutes = diffMillis / 60_000L
            val hours = minutes / 60L
            return when {
                minutes < 1L -> "Just now"
                minutes < 60L -> "${minutes}m ago"
                hours < 24L -> "${hours}h ago"
                else -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(updatedAtMillis))
            }
        }
    }
}
