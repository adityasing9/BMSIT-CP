package com.continueo.autologin.security

import android.content.Context
import com.continueo.autologin.models.AttendanceSnapshot
import com.continueo.autologin.models.SubjectAttendance
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stores non-sensitive parsed attendance display snapshots per account ID
 * for fast rendering in the home screen widget without keystore latency.
 */
class AttendanceSnapshotStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveSnapshot(snapshot: AttendanceSnapshot) {
        if (!snapshot.hasData) return
        val json = serialize(snapshot)
        prefs.edit().putString("$KEY_PREFIX${snapshot.accountId}", json).apply()
    }

    fun getSnapshot(accountId: String): AttendanceSnapshot? {
        val raw = prefs.getString("$KEY_PREFIX$accountId", null) ?: return null
        return deserialize(raw)
    }

    fun deleteSnapshot(accountId: String) {
        prefs.edit().remove("$KEY_PREFIX$accountId").apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "bmsit_cp_attendance_snapshots"
        private const val KEY_PREFIX = "snapshot_"

        fun serialize(snapshot: AttendanceSnapshot): String {
            val array = JSONArray()
            for (sub in snapshot.subjects) {
                array.put(
                    JSONObject().apply {
                        put("name", sub.name)
                        put("percentage", sub.clampedPercentage)
                        put("detail", sub.detail)
                    }
                )
            }
            return JSONObject().apply {
                put("accountId", snapshot.accountId)
                put("accountName", snapshot.accountName)
                if (snapshot.overallPercentage != null) {
                    put("overallPercentage", snapshot.overallPercentage.coerceIn(0, 100))
                }
                put("subjects", array)
                put("updatedAtMillis", snapshot.updatedAtMillis)
            }.toString()
        }

        fun deserialize(jsonStr: String): AttendanceSnapshot? {
            return try {
                val obj = JSONObject(jsonStr)
                val accountId = obj.optString("accountId", "")
                if (accountId.isBlank()) return null
                val accountName = obj.optString("accountName", "")
                val overall = if (obj.has("overallPercentage") && !obj.isNull("overallPercentage")) {
                    obj.optInt("overallPercentage").coerceIn(0, 100)
                } else {
                    null
                }
                val updatedAt = obj.optLong("updatedAtMillis", 0L)
                val subjects = mutableListOf<SubjectAttendance>()
                val arr = obj.optJSONArray("subjects")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i) ?: continue
                        val name = item.optString("name", "").trim()
                        if (name.isBlank()) continue
                        val pct = item.optInt("percentage", -1)
                        if (pct !in 0..100) continue
                        val detail = item.optString("detail", "").trim()
                        subjects.add(SubjectAttendance(name = name, percentage = pct, detail = detail))
                    }
                }
                AttendanceSnapshot(
                    accountId = accountId,
                    accountName = accountName,
                    overallPercentage = overall,
                    subjects = subjects,
                    updatedAtMillis = updatedAt
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
