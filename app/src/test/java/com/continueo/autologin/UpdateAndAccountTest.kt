package com.continueo.autologin

import com.continueo.autologin.models.AccountProfile
import com.continueo.autologin.models.Credentials
import com.continueo.autologin.utils.FeedbackCategory
import com.continueo.autologin.utils.FeedbackManager
import com.continueo.autologin.utils.UpdateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
