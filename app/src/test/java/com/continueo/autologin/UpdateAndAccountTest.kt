package com.continueo.autologin

import com.continueo.autologin.models.AccountProfile
import com.continueo.autologin.models.Credentials
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
}
