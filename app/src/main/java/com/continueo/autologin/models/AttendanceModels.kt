package com.continueo.autologin.models

enum class AttendanceStatus {
    SAFE,
    WARNING,
    DANGER
}

data class SubjectAttendance(
    val name: String,
    val percentage: Int,
    val detail: String = ""
) {
    val clampedPercentage: Int
        get() = percentage.coerceIn(0, 100)

    val status: AttendanceStatus
        get() = when {
            clampedPercentage >= 85 -> AttendanceStatus.SAFE
            clampedPercentage >= 75 -> AttendanceStatus.WARNING
            else -> AttendanceStatus.DANGER
        }
}

data class AttendanceSnapshot(
    val accountId: String,
    val accountName: String,
    val overallPercentage: Int?,
    val subjects: List<SubjectAttendance>,
    val updatedAtMillis: Long
) {
    val displayOverallPercentage: Int?
        get() = overallPercentage?.coerceIn(0, 100)
            ?: if (subjects.isNotEmpty()) {
                subjects.map { it.clampedPercentage }.average().toInt().coerceIn(0, 100)
            } else {
                null
            }

    val hasData: Boolean
        get() = subjects.isNotEmpty() || displayOverallPercentage != null
}
