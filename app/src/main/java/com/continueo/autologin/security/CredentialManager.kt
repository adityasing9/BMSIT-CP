package com.continueo.autologin.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.continueo.autologin.models.Credentials

class CredentialManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveCredentials(credentials: Credentials) {
        sharedPreferences.edit()
            .putString(KEY_USN, credentials.usn)
            .putString(KEY_DOB_DAY, credentials.dobDay)
            .putString(KEY_DOB_MONTH, credentials.dobMonth)
            .putString(KEY_DOB_YEAR, credentials.dobYear)
            .putString(KEY_ID_CARD, credentials.idCardNumber)
            .apply()
    }

    fun loadCredentials(): Credentials? {
        val usn = sharedPreferences.getString(KEY_USN, null)
        val dobDay = sharedPreferences.getString(KEY_DOB_DAY, null)
        val dobMonth = sharedPreferences.getString(KEY_DOB_MONTH, null)
        val dobYear = sharedPreferences.getString(KEY_DOB_YEAR, null)
        val idCard = sharedPreferences.getString(KEY_ID_CARD, null)

        if (usn != null && dobDay != null && dobMonth != null && dobYear != null && idCard != null) {
            return Credentials(usn, dobDay, dobMonth, dobYear, idCard)
        }
        return null
    }

    fun deleteCredentials() {
        val desktopMode = isDesktopMode()
        sharedPreferences.edit().clear().apply()
        setDesktopMode(desktopMode)
    }

    fun hasCredentials(): Boolean {
        return sharedPreferences.contains(KEY_USN)
    }

    fun isDesktopMode(): Boolean {
        return sharedPreferences.getBoolean(KEY_DESKTOP_MODE, true)
    }

    fun setDesktopMode(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_DESKTOP_MODE, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "continueo_credentials"
        private const val KEY_USN = "key_usn"
        private const val KEY_DOB_DAY = "key_dob_day"
        private const val KEY_DOB_MONTH = "key_dob_month"
        private const val KEY_DOB_YEAR = "key_dob_year"
        private const val KEY_ID_CARD = "key_id_card"
        private const val KEY_DESKTOP_MODE = "key_desktop_mode"
    }
}
