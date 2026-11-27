package com.continueo.autologin.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.continueo.autologin.security.AttendanceSnapshotStore
import com.continueo.autologin.security.CredentialManager
import com.continueo.autologin.widget.AttendanceWidgetProvider

class AttendanceNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SWITCH_ACCOUNT) return

        try {
            val credentialManager = CredentialManager(context)
            val accounts = credentialManager.getAllAccounts()
            if (accounts.size > 1) {
                val activeId = credentialManager.getActiveAccountId()
                val currentIdx = accounts.indexOfFirst { it.id == activeId }
                val nextIdx = if (currentIdx >= 0) (currentIdx + 1) % accounts.size else 0
                val nextAccount = accounts[nextIdx]
                credentialManager.setActiveAccountId(nextAccount.id)

                val snapshot = AttendanceSnapshotStore(context).getSnapshot(nextAccount.id)
                if (snapshot != null && snapshot.hasData) {
                    AttendanceNotificationManager.notifyFromSync(
                        context = context,
                        snapshot = snapshot,
                        activeAccount = nextAccount,
                        accounts = accounts,
                        forceShow = true,
                        silentUpdate = true
                    )
                } else {
                    AttendanceNotificationManager.notifyAccountSwitchedNoData(
                        context = context,
                        activeAccount = nextAccount,
                        accounts = accounts
                    )
                }
            }
            AttendanceWidgetProvider.refreshAllWidgets(context)
        } catch (_: Exception) {
            // Resilient if encrypted preferences are temporarily unavailable
        }
    }

    companion object {
        const val ACTION_SWITCH_ACCOUNT = "com.continueo.autologin.notifications.ACTION_SWITCH_ACCOUNT"
    }
}
