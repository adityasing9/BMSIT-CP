package com.continueo.autologin

import com.continueo.autologin.automation.AttendanceExtractor
import com.continueo.autologin.models.AccountProfile
import com.continueo.autologin.models.AttendanceSnapshot
import com.continueo.autologin.models.AttendanceStatus
import com.continueo.autologin.models.Credentials
import com.continueo.autologin.models.SubjectAttendance
import com.continueo.autologin.utils.FeedbackCategory
import com.continueo.autologin.utils.FeedbackManager
import com.continueo.autologin.utils.UpdateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateAndAccountTest {

    @Test
    fun testVersionComparison() {
        assertTrue(UpdateManager.isNewerVersion("1.0.1", "1.0.0"))
        assertTrue(UpdateManager.isNewerVersion("1.1.0", "1.0.0"))
        assertTrue(UpdateManager.isNewerVersion("v2.0.0", "1.9.9"))
        assertTrue(UpdateManager.isNewerVersion("V1.0.5", "1.0.4"))

        assertFalse(UpdateManager.isNewerVersion("1.0.0", "1.0.0"))
        assertFalse(UpdateManager.isNewerVersion("0.9.9", "1.0.0"))
        assertFalse(UpdateManager.isNewerVersion("1.0.0", "1.0.1"))
        assertFalse(UpdateManager.isNewerVersion("1.0", "1.0.0"))
    }

    @Test
    fun testAccountProfileDisplayNameAndMasking() {
        val creds = Credentials(
            usn = "1BM22CS001",
            dobDay = "01",
            dobMonth = "01",
            dobYear = "2004",
            idCardNumber = "ID123456"
        )

        val accountWithLabel = AccountProfile(
            label = "Aditya",
            credentials = creds
        )
        assertEquals("Aditya", accountWithLabel.getDisplayName())
        assertTrue(accountWithLabel.maskedSummary().contains("Aditya"))
        assertTrue(accountWithLabel.maskedSummary().contains("1B***01"))

        val accountWithoutLabel = AccountProfile(
            label = "",
            credentials = creds
        )
        assertEquals("1BM22CS001", accountWithoutLabel.getDisplayName())
        assertTrue(accountWithoutLabel.maskedSummary().contains("1B***01"))
    }

    @Test
    fun testFeedbackEmailValidation() {
        assertTrue(FeedbackManager.isValidEmail("student@bmsit.in"))
        assertFalse(FeedbackManager.isValidEmail("+919876543210"))
        assertFalse(FeedbackManager.isValidEmail(""))
    }

    @Test
    fun testFeedbackPayloadAnonymousAndWithEmail() {
        val anonPayload = FeedbackManager.buildPayloadFields(
            accessKey = "test-key",
            category = FeedbackCategory.COMPLAINT,
            subject = "",
            message = "Marks page table alignment",
            contact = "",
            appVersion = "1.2.15",
            androidVersion = "Android 14 (API 34)",
            deviceModel = "Pixel 8",
            includeDeviceInfo = false
        )
        assertEquals("test-key", anonPayload["access_key"])
        assertEquals("BMSIT CP [Complaint]: Complaint", anonPayload["subject"])
        assertEquals("Anonymous", anonPayload["contact"])
        assertFalse(anonPayload.containsKey("email"))
        assertFalse(anonPayload.containsKey("app_version"))
        assertFalse(anonPayload.containsKey("usn"))

        val emailPayload = FeedbackManager.buildPayloadFields(
            accessKey = "test-key",
            category = FeedbackCategory.BUG,
            subject = "Login timeout",
            message = "Stuck on ID page",
            contact = "student@bmsit.in",
            appVersion = "1.2.15",
            androidVersion = "Android 14 (API 34)",
            deviceModel = "Pixel 8",
            includeDeviceInfo = true
        )
        assertEquals("student@bmsit.in", emailPayload["email"])
        assertEquals("student@bmsit.in", emailPayload["replyto"])
        assertEquals("v1.2.15", emailPayload["app_version"])
        assertEquals("Pixel 8", emailPayload["device"])
    }

    @Test
    fun testSubjectAttendanceClampingAndThresholds() {
        val safe = SubjectAttendance("Algorithms", 85)
        val over = SubjectAttendance("Operating Systems", 120)
        val warnHigh = SubjectAttendance("Networks", 84)
        val warnLow = SubjectAttendance("DBMS", 75)
        val danger = SubjectAttendance("Math", 74)
        val negative = SubjectAttendance("Lab", -5)

        assertEquals(AttendanceStatus.SAFE, safe.status)
        assertEquals(100, over.clampedPercentage)
        assertEquals(AttendanceStatus.SAFE, over.status)

        assertEquals(AttendanceStatus.WARNING, warnHigh.status)
        assertEquals(AttendanceStatus.WARNING, warnLow.status)

        assertEquals(AttendanceStatus.DANGER, danger.status)
        assertEquals(0, negative.clampedPercentage)
        assertEquals(AttendanceStatus.DANGER, negative.status)
    }

    @Test
    fun testAttendanceSnapshotOverallAndHasData() {
        val explicitOverall = AttendanceSnapshot(
            accountId = "acc-1",
            accountName = "Aditya",
            overallPercentage = 104,
            subjects = listOf(SubjectAttendance("CS101", 80)),
            updatedAtMillis = 123456L
        )
        assertEquals(100, explicitOverall.displayOverallPercentage)
        assertTrue(explicitOverall.hasData)

        val derivedOverall = AttendanceSnapshot(
            accountId = "acc-2",
            accountName = "Student",
            overallPercentage = null,
            subjects = listOf(
                SubjectAttendance("Sub 1", 90),
                SubjectAttendance("Sub 2", 80),
                SubjectAttendance("Sub 3", 70)
            ),
            updatedAtMillis = 123456L
        )
        assertEquals(80, derivedOverall.displayOverallPercentage)
        assertTrue(derivedOverall.hasData)

        val overallOnly = AttendanceSnapshot(
            accountId = "acc-3",
            accountName = "Student",
            overallPercentage = 78,
            subjects = emptyList(),
            updatedAtMillis = 123456L
        )
        assertTrue(overallOnly.hasData)

        val empty = AttendanceSnapshot(
            accountId = "acc-4",
            accountName = "Student",
            overallPercentage = null,
            subjects = emptyList(),
            updatedAtMillis = 0L
        )
        assertNull(empty.displayOverallPercentage)
        assertFalse(empty.hasData)
    }

    @Test
    fun testAttendanceExtractorUnquotingAndBlankGuard() {
        val quoted = "\"{\\\"found\\\":true,\\\"overallPercentage\\\":88}\""
        assertEquals(
            "{\"found\":true,\"overallPercentage\":88}",
            AttendanceExtractor.unquoteEvaluateJavascriptResult(quoted)
        )

        val escapedUnicodeAndSlash = """"{\"name\":\"CS\u0026E\/AI\",\"pct\":91}""""
        assertEquals(
            """{"name":"CS&E/AI","pct":91}""",
            AttendanceExtractor.unquoteEvaluateJavascriptResult(escapedUnicodeAndSlash)
        )

        val raw = "{\"found\":false}"
        assertEquals(raw, AttendanceExtractor.unquoteEvaluateJavascriptResult(raw))

        assertNull(AttendanceExtractor.parseExtractedJson("", "acc-1", "Student"))
        assertNull(AttendanceExtractor.parseExtractedJson("   ", "acc-1", "Student"))
        assertNull(AttendanceExtractor.parseExtractedJson("null", "acc-1", "Student"))
    }
}
