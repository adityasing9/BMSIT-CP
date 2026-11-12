package com.continueo.autologin.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.continueo.autologin.models.AccountProfile
import com.continueo.autologin.models.Credentials
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

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

    init {
        migrateLegacyCredentialsIfNeeded()
    }

    private fun migrateLegacyCredentialsIfNeeded() {
        if (!sharedPreferences.contains(KEY_ACCOUNTS_JSON) && sharedPreferences.contains(KEY_USN)) {
            val usn = sharedPreferences.getString(KEY_USN, null)
            val dobDay = sharedPreferences.getString(KEY_DOB_DAY, null)
            val dobMonth = sharedPreferences.getString(KEY_DOB_MONTH, null)
            val dobYear = sharedPreferences.getString(KEY_DOB_YEAR, null)
            val idCard = sharedPreferences.getString(KEY_ID_CARD, null)

            if (usn != null && dobDay != null && dobMonth != null && dobYear != null && idCard != null) {
                val legacyCreds = Credentials(usn, dobDay, dobMonth, dobYear, idCard)
                if (legacyCreds.isValid()) {
                    val defaultAccount = AccountProfile(
                        id = UUID.randomUUID().toString(),
                        label = usn,
                        credentials = legacyCreds
                    )
                    val accounts = listOf(defaultAccount)
                    sharedPreferences.edit()
                        .putString(KEY_ACCOUNTS_JSON, serializeAccounts(accounts))
                        .putString(KEY_ACTIVE_ACCOUNT_ID, defaultAccount.id)
                        .apply()
                }
            }
        }
    }

    fun getAllAccounts(): List<AccountProfile> {
        val jsonStr = sharedPreferences.getString(KEY_ACCOUNTS_JSON, null)
        return deserializeAccounts(jsonStr)
    }

    fun getActiveAccount(): AccountProfile? {
        val accounts = getAllAccounts()
        if (accounts.isEmpty()) return null
        val activeId = sharedPreferences.getString(KEY_ACTIVE_ACCOUNT_ID, null)
        return accounts.find { it.id == activeId } ?: accounts.first().also {
            setActiveAccountId(it.id)
        }
    }

    fun getActiveAccountId(): String? {
        return getActiveAccount()?.id
    }

    fun setActiveAccountId(accountId: String) {
        val accounts = getAllAccounts()
        val account = accounts.find { it.id == accountId } ?: return
        sharedPreferences.edit()
            .putString(KEY_ACTIVE_ACCOUNT_ID, account.id)
            .putString(KEY_USN, account.credentials.usn)
            .putString(KEY_DOB_DAY, account.credentials.dobDay)
            .putString(KEY_DOB_MONTH, account.credentials.dobMonth)
            .putString(KEY_DOB_YEAR, account.credentials.dobYear)
            .putString(KEY_ID_CARD, account.credentials.idCardNumber)
            .apply()
    }

    fun saveAccount(account: AccountProfile) {
        val accounts = getAllAccounts().toMutableList()
        val existingIndex = accounts.indexOfFirst { it.id == account.id }
        if (existingIndex >= 0) {
            accounts[existingIndex] = account
        } else {
            accounts.add(account)
        }

        val editor = sharedPreferences.edit()
            .putString(KEY_ACCOUNTS_JSON, serializeAccounts(accounts))

        // If this is the only account or currently active, ensure it is set as active
        val currentActiveId = sharedPreferences.getString(KEY_ACTIVE_ACCOUNT_ID, null)
        if (currentActiveId == null || currentActiveId == account.id || accounts.size == 1) {
            editor.putString(KEY_ACTIVE_ACCOUNT_ID, account.id)
            editor.putString(KEY_USN, account.credentials.usn)
            editor.putString(KEY_DOB_DAY, account.credentials.dobDay)
            editor.putString(KEY_DOB_MONTH, account.credentials.dobMonth)
            editor.putString(KEY_DOB_YEAR, account.credentials.dobYear)
            editor.putString(KEY_ID_CARD, account.credentials.idCardNumber)
        }

        editor.apply()
    }

    fun deleteAccount(accountId: String) {
        val accounts = getAllAccounts().toMutableList()
        val removeIndex = accounts.indexOfFirst { it.id == accountId }
        if (removeIndex < 0) return

        accounts.removeAt(removeIndex)
        val editor = sharedPreferences.edit()
            .putString(KEY_ACCOUNTS_JSON, serializeAccounts(accounts))

        val currentActiveId = sharedPreferences.getString(KEY_ACTIVE_ACCOUNT_ID, null)
        if (currentActiveId == accountId) {
            if (accounts.isNotEmpty()) {
                val nextActive = accounts.first()
                editor.putString(KEY_ACTIVE_ACCOUNT_ID, nextActive.id)
                editor.putString(KEY_USN, nextActive.credentials.usn)
                editor.putString(KEY_DOB_DAY, nextActive.credentials.dobDay)
                editor.putString(KEY_DOB_MONTH, nextActive.credentials.dobMonth)
                editor.putString(KEY_DOB_YEAR, nextActive.credentials.dobYear)
                editor.putString(KEY_ID_CARD, nextActive.credentials.idCardNumber)
            } else {
                editor.remove(KEY_ACTIVE_ACCOUNT_ID)
                editor.remove(KEY_USN)
                editor.remove(KEY_DOB_DAY)
                editor.remove(KEY_DOB_MONTH)
                editor.remove(KEY_DOB_YEAR)
                editor.remove(KEY_ID_CARD)
            }
        }
        editor.apply()
    }

    /**
     * Backward-compatible convenience method: saves credentials as the active account.
     */
    fun saveCredentials(credentials: Credentials, label: String = "") {
        val currentActive = getActiveAccount()
        val account = if (currentActive != null) {
            currentActive.copy(
                label = if (label.isNotBlank()) label else currentActive.label,
                credentials = credentials
            )
        } else {
            AccountProfile(
                id = UUID.randomUUID().toString(),
                label = if (label.isNotBlank()) label else credentials.usn,
                credentials = credentials
            )
        }
        saveAccount(account)
    }

    fun loadCredentials(): Credentials? {
        val active = getActiveAccount()
        if (active != null) {
            return active.credentials
        }

        // Fallback to legacy single-account preference keys
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
        return getAllAccounts().isNotEmpty() || sharedPreferences.contains(KEY_USN)
    }

    fun isDesktopMode(): Boolean {
        return sharedPreferences.getBoolean(KEY_DESKTOP_MODE, true)
    }

    fun setDesktopMode(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_DESKTOP_MODE, enabled).apply()
    }

    private fun serializeAccounts(accounts: List<AccountProfile>): String {
        val array = JSONArray()
        for (acc in accounts) {
            val obj = JSONObject().apply {
                put("id", acc.id)
                put("label", acc.label)
                put("usn", acc.credentials.usn)
                put("dobDay", acc.credentials.dobDay)
                put("dobMonth", acc.credentials.dobMonth)
                put("dobYear", acc.credentials.dobYear)
                put("idCardNumber", acc.credentials.idCardNumber)
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun deserializeAccounts(jsonStr: String?): MutableList<AccountProfile> {
        if (jsonStr.isNullOrBlank()) return mutableListOf()
        val list = mutableListOf<AccountProfile>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optString("id", UUID.randomUUID().toString())
                val label = obj.optString("label", "")
                val usn = obj.optString("usn", "")
                val dobDay = obj.optString("dobDay", "")
                val dobMonth = obj.optString("dobMonth", "")
                val dobYear = obj.optString("dobYear", "")
                val idCardNumber = obj.optString("idCardNumber", "")
                val creds = Credentials(usn, dobDay, dobMonth, dobYear, idCardNumber)
                if (creds.isValid()) {
                    list.add(AccountProfile(id = id, label = label, credentials = creds))
                }
            }
        } catch (_: Exception) {}
        return list
    }

    companion object {
        private const val PREFS_NAME = "continueo_credentials"
        private const val KEY_ACCOUNTS_JSON = "key_accounts_json"
        private const val KEY_ACTIVE_ACCOUNT_ID = "key_active_account_id"
        private const val KEY_USN = "key_usn"
        private const val KEY_DOB_DAY = "key_dob_day"
        private const val KEY_DOB_MONTH = "key_dob_month"
        private const val KEY_DOB_YEAR = "key_dob_year"
        private const val KEY_ID_CARD = "key_id_card"
        private const val KEY_DESKTOP_MODE = "key_desktop_mode"
    }
}
