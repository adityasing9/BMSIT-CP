package com.continueo.autologin.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.continueo.autologin.MainActivity
import com.continueo.autologin.R
import com.continueo.autologin.automation.ContineoSelectors
import com.continueo.autologin.models.AttendanceSnapshot
import com.continueo.autologin.security.AttendanceSnapshotStore
import com.continueo.autologin.security.CredentialManager
import com.continueo.autologin.utils.AttendanceUiHelper
import com.continueo.autologin.utils.SubjectGridSpec

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

        private val GRID_SPEC = SubjectGridSpec(
            rowIds = intArrayOf(
                R.id.widget_grid_row_0,
                R.id.widget_grid_row_1,
                R.id.widget_grid_row_2,
                R.id.widget_grid_row_3
            ),
            cellIds = intArrayOf(
                R.id.widget_cell_0,
                R.id.widget_cell_1,
                R.id.widget_cell_2,
                R.id.widget_cell_3,
                R.id.widget_cell_4,
                R.id.widget_cell_5,
                R.id.widget_cell_6,
                R.id.widget_cell_7
            ),
            nameIds = intArrayOf(
                R.id.widget_cell_0_name,
                R.id.widget_cell_1_name,
                R.id.widget_cell_2_name,
                R.id.widget_cell_3_name,
                R.id.widget_cell_4_name,
                R.id.widget_cell_5_name,
                R.id.widget_cell_6_name,
                R.id.widget_cell_7_name
            ),
            pctIds = intArrayOf(
                R.id.widget_cell_0_pct,
                R.id.widget_cell_1_pct,
                R.id.widget_cell_2_pct,
                R.id.widget_cell_3_pct,
                R.id.widget_cell_4_pct,
                R.id.widget_cell_5_pct,
                R.id.widget_cell_6_pct,
                R.id.widget_cell_7_pct
            ),
            safeBarIds = intArrayOf(
                R.id.widget_cell_0_progress_safe,
                R.id.widget_cell_1_progress_safe,
                R.id.widget_cell_2_progress_safe,
                R.id.widget_cell_3_progress_safe,
                R.id.widget_cell_4_progress_safe,
                R.id.widget_cell_5_progress_safe,
                R.id.widget_cell_6_progress_safe,
                R.id.widget_cell_7_progress_safe
            ),
            warnBarIds = intArrayOf(
                R.id.widget_cell_0_progress_warn,
                R.id.widget_cell_1_progress_warn,
                R.id.widget_cell_2_progress_warn,
                R.id.widget_cell_3_progress_warn,
                R.id.widget_cell_4_progress_warn,
                R.id.widget_cell_5_progress_warn,
                R.id.widget_cell_6_progress_warn,
                R.id.widget_cell_7_progress_warn
            ),
            dangerBarIds = intArrayOf(
                R.id.widget_cell_0_progress_danger,
                R.id.widget_cell_1_progress_danger,
                R.id.widget_cell_2_progress_danger,
                R.id.widget_cell_3_progress_danger,
                R.id.widget_cell_4_progress_danger,
                R.id.widget_cell_5_progress_danger,
                R.id.widget_cell_6_progress_danger,
                R.id.widget_cell_7_progress_danger
            )
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
                val options = try {
                    appWidgetManager.getAppWidgetOptions(appWidgetId)
                } catch (_: Exception) {
                    null
                }
                val views = buildRemoteViews(context, options)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }

        private fun buildRemoteViews(context: Context, options: Bundle? = null): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_attendance)

            val accountState = try {
                val credentialManager = CredentialManager(context)
                Pair(credentialManager.getAllAccounts(), credentialManager.getActiveAccount())
            } catch (_: Exception) {
                Pair(emptyList(), null)
            }

            val accounts = accountState.first
            val activeAccount = accountState.second
            val snapshot: AttendanceSnapshot? = activeAccount?.let {
                AttendanceSnapshotStore(context).getSnapshot(it.id)
            }

            val accountDisplayName = AttendanceUiHelper.formatSafeAccountName(activeAccount, accounts)
            val overallPct = snapshot?.displayOverallPercentage
            val subtitleText = when {
                activeAccount == null -> "Open app to set up"
                snapshot == null || !snapshot.hasData -> "Not synced yet"
                snapshot.subjects.isNotEmpty() -> "${AttendanceUiHelper.formatSyncTime(snapshot.updatedAtMillis)} • ${snapshot.subjects.size} subj"
                else -> AttendanceUiHelper.formatSyncTime(snapshot.updatedAtMillis)
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
                AttendanceUiHelper.drawAttendanceRingBitmap(context, overallPct)
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
                val minHeightDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) ?: 0
                val maxVisibleCells = if (minHeightDp in 1..145) 4 else GRID_SPEC.cellIds.size
                val shown = AttendanceUiHelper.bindSubjectsGrid(
                    context = context,
                    views = views,
                    spec = GRID_SPEC,
                    subjects = snapshot.subjects,
                    maxVisibleCells = maxVisibleCells
                )
                when {
                    snapshot.subjects.isEmpty() -> {
                        val pct = overallPct ?: 0
                        views.setViewVisibility(R.id.widget_more_text, View.VISIBLE)
                        views.setTextViewText(R.id.widget_more_text, "Overall attendance: $pct%")
                    }
                    snapshot.subjects.size > shown -> {
                        views.setViewVisibility(R.id.widget_more_text, View.VISIBLE)
                        views.setTextViewText(
                            R.id.widget_more_text,
                            "+${snapshot.subjects.size - shown} more subjects"
                        )
                    }
                    else -> {
                        views.setViewVisibility(R.id.widget_more_text, View.INVISIBLE)
                        views.setTextViewText(R.id.widget_more_text, "")
                    }
                }
            }

            bindClickActions(context, views)
            return views
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
    }
}
