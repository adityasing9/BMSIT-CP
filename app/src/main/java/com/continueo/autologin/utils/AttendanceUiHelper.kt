package com.continueo.autologin.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.continueo.autologin.R
import com.continueo.autologin.models.AccountProfile
import com.continueo.autologin.models.AttendanceSnapshot
import com.continueo.autologin.models.AttendanceStatus
import com.continueo.autologin.models.SubjectAttendance
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class AttendanceAlertSummary(
    val overallPercentage: Int?,
    val belowThresholdCount: Int,
    val dangerCount: Int,
    val sortedSubjects: List<SubjectAttendance>,
    val lowestSubject: SubjectAttendance?,
    val severity: AttendanceStatus,
    val headline: String,
    val subline: String,
    val badgeText: String
)

data class SubjectGridSpec(
    val rowIds: IntArray,
    val cellIds: IntArray,
    val nameIds: IntArray,
    val pctIds: IntArray,
    val safeBarIds: IntArray,
    val warnBarIds: IntArray,
    val dangerBarIds: IntArray
)

object AttendanceUiHelper {

    fun buildAlertSummary(
        snapshot: AttendanceSnapshot,
        thresholdPercent: Int = 85
    ): AttendanceAlertSummary {
        val threshold = thresholdPercent.coerceIn(50, 95)
        val sorted = snapshot.subjects.sortedBy { it.clampedPercentage }
        val overall = snapshot.displayOverallPercentage
        val belowCount = sorted.count { it.clampedPercentage < threshold }
        val dangerCount = sorted.count { it.clampedPercentage < 75 }
        val lowest = sorted.firstOrNull()

        val severity = when {
            dangerCount > 0 || (overall != null && overall < 75) -> AttendanceStatus.DANGER
            belowCount > 0 || (overall != null && overall < threshold) -> AttendanceStatus.WARNING
            else -> AttendanceStatus.SAFE
        }

        val headline = when {
            sorted.isEmpty() -> {
                if (overall != null) "Overall attendance: $overall%" else "Attendance synced"
            }
            dangerCount > 0 -> {
                val extra = if (belowCount > dangerCount) " • $belowCount below $threshold%" else ""
                "$dangerCount in shortage (<75%)$extra"
            }
            belowCount > 0 -> "$belowCount subject${if (belowCount == 1) "" else "s"} below $threshold%"
            else -> "All ${sorted.size} subjects ≥ $threshold%"
        }

        val lowestDetail = lowest?.let { "Lowest: ${it.name} (${it.clampedPercentage}%)" }
        val subline = when {
            lowestDetail != null && overall != null -> "$overall% avg • $lowestDetail"
            lowestDetail != null -> lowestDetail
            overall != null -> "Updated overall summary"
            else -> "Tap to open BMSIT CP"
        }

        val badgeText = when (severity) {
            AttendanceStatus.DANGER -> "SHORTAGE"
            AttendanceStatus.WARNING -> "WATCH"
            AttendanceStatus.SAFE -> "ON TRACK"
        }

        return AttendanceAlertSummary(
            overallPercentage = overall,
            belowThresholdCount = belowCount,
            dangerCount = dangerCount,
            sortedSubjects = sorted,
            lowestSubject = lowest,
            severity = severity,
            headline = headline,
            subline = subline,
            badgeText = badgeText
        )
    }

    fun shouldPostNotification(
        snapshot: AttendanceSnapshot,
        onlyBelowThreshold: Boolean,
        thresholdPercent: Int = 85
    ): Boolean {
        if (!snapshot.hasData) return false
        if (!onlyBelowThreshold) return true
        val summary = buildAlertSummary(snapshot, thresholdPercent)
        val overall = snapshot.displayOverallPercentage
        return summary.belowThresholdCount > 0 || (overall != null && overall < thresholdPercent.coerceIn(50, 95))
    }

    fun formatSafeAccountName(
        account: AccountProfile?,
        accounts: List<AccountProfile>
    ): String {
        if (account == null) return "No account configured"
        val rawLabel = account.getDisplayName().trim()
        val safeLabel = if (rawLabel.isBlank() || rawLabel.equals(account.credentials.usn.trim(), ignoreCase = true)) {
            maskIdentifier(account.credentials.usn.trim())
        } else {
            rawLabel
        }
        return if (accounts.size > 1) {
            val idx = accounts.indexOfFirst { it.id == account.id }.coerceAtLeast(0)
            "$safeLabel (${idx + 1}/${accounts.size})"
        } else {
            safeLabel
        }
    }

    fun maskIdentifier(raw: String): String {
        if (raw.length < 5) return "Account"
        return "${raw.take(2)}***${raw.takeLast(2)}"
    }

    fun formatSyncTime(
        updatedAtMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        if (updatedAtMillis <= 0L) return "Synced"
        val diffMillis = (nowMillis - updatedAtMillis).coerceAtLeast(0L)
        val minutes = diffMillis / 60_000L
        val hours = minutes / 60L
        return when {
            minutes < 1L -> "Just now"
            minutes < 60L -> "${minutes}m ago"
            hours < 24L -> "${hours}h ago"
            else -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(updatedAtMillis))
        }
    }

    fun statusColorRes(status: AttendanceStatus): Int {
        return when (status) {
            AttendanceStatus.SAFE -> R.color.attendance_safe
            AttendanceStatus.WARNING -> R.color.attendance_warn
            AttendanceStatus.DANGER -> R.color.attendance_danger
        }
    }

    fun drawAttendanceRingBitmap(
        context: Context,
        overallPercentage: Int?,
        sizePx: Int = 144,
        strokePx: Float = 13f,
        textColorRes: Int = R.color.widget_text_primary,
        trackColorRes: Int = R.color.widget_track
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val trackColor = ContextCompat.getColor(context, trackColorRes)
        val textColor = ContextCompat.getColor(context, textColorRes)
        val ringColorRes = when {
            overallPercentage == null -> trackColorRes
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

        val label = if (overallPercentage != null) "${overallPercentage.coerceIn(0, 100)}%" else "N/A"
        val scale = sizePx / 144f
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            textSize = scale * when {
                overallPercentage == null -> 26f
                label.length >= 4 -> 30f
                else -> 34f
            }
        }
        val fontMetrics = textPaint.fontMetrics
        val textY = sizePx / 2f - (fontMetrics.ascent + fontMetrics.descent) / 2f
        canvas.drawText(label, sizePx / 2f, textY, textPaint)

        return bitmap
    }

    fun bindSubjectsGrid(
        context: Context,
        views: RemoteViews,
        spec: SubjectGridSpec,
        subjects: List<SubjectAttendance>,
        maxVisibleCells: Int = spec.cellIds.size
    ): Int {
        val cellLimit = maxVisibleCells.coerceIn(0, spec.cellIds.size)
        val displaySubjects = subjects.take(cellLimit)

        for (row in spec.rowIds.indices) {
            val firstCellIndex = row * 2
            if (firstCellIndex >= displaySubjects.size) {
                views.setViewVisibility(spec.rowIds[row], View.GONE)
                continue
            }
            views.setViewVisibility(spec.rowIds[row], View.VISIBLE)

            for (col in 0..1) {
                val cellIndex = firstCellIndex + col
                if (cellIndex >= displaySubjects.size) {
                    views.setViewVisibility(spec.cellIds[cellIndex], View.INVISIBLE)
                } else {
                    views.setViewVisibility(spec.cellIds[cellIndex], View.VISIBLE)
                    bindSubjectCell(context, views, spec, cellIndex, displaySubjects[cellIndex])
                }
            }
        }
        return displaySubjects.size
    }

    fun toLocalDayKey(timeMillis: Long): String {
        if (timeMillis <= 0L) return ""
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timeMillis))
    }

    fun shouldSendDailySyncReminder(
        hasAccount: Boolean,
        snapshotUpdatedAtMillis: Long,
        nowMillis: Long,
        lastReminderDayKey: String
    ): Boolean {
        if (!hasAccount) return false
        val todayKey = toLocalDayKey(nowMillis)
        if (todayKey.isBlank() || todayKey == lastReminderDayKey) return false
        val snapshotDayKey = toLocalDayKey(snapshotUpdatedAtMillis)
        return snapshotDayKey != todayKey
    }

    fun computeInitialDelayMillis(
        nowMillis: Long,
        targetHour: Int,
        targetMinute: Int
    ): Long {
        val safeHour = targetHour.coerceIn(0, 23)
        val safeMinute = targetMinute.coerceIn(0, 59)
        val nextRun = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, safeHour)
            set(Calendar.MINUTE, safeMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= nowMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return (nextRun.timeInMillis - nowMillis).coerceAtLeast(60_000L)
    }

    private fun bindSubjectCell(
        context: Context,
        views: RemoteViews,
        spec: SubjectGridSpec,
        index: Int,
        subject: SubjectAttendance
    ) {
        val pct = subject.clampedPercentage
        views.setTextViewText(spec.nameIds[index], subject.name)
        views.setTextViewText(spec.pctIds[index], "$pct%")
        views.setTextColor(spec.pctIds[index], ContextCompat.getColor(context, statusColorRes(subject.status)))

        when (subject.status) {
            AttendanceStatus.SAFE -> {
                views.setViewVisibility(spec.safeBarIds[index], View.VISIBLE)
                views.setProgressBar(spec.safeBarIds[index], 100, pct, false)
                views.setViewVisibility(spec.warnBarIds[index], View.GONE)
                views.setViewVisibility(spec.dangerBarIds[index], View.GONE)
            }
            AttendanceStatus.WARNING -> {
                views.setViewVisibility(spec.safeBarIds[index], View.GONE)
                views.setViewVisibility(spec.warnBarIds[index], View.VISIBLE)
                views.setProgressBar(spec.warnBarIds[index], 100, pct, false)
                views.setViewVisibility(spec.dangerBarIds[index], View.GONE)
            }
            AttendanceStatus.DANGER -> {
                views.setViewVisibility(spec.safeBarIds[index], View.GONE)
                views.setViewVisibility(spec.warnBarIds[index], View.GONE)
                views.setViewVisibility(spec.dangerBarIds[index], View.VISIBLE)
                views.setProgressBar(spec.dangerBarIds[index], 100, pct, false)
            }
        }
    }
}
