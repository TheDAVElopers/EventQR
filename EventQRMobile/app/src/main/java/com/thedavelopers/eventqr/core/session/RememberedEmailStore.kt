package com.thedavelopers.eventqr.core.session

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Remembers only the e-mail address typed at sign-in ("Remember my email"). It lives in its own
 * encrypted preferences file, separate from [SessionManager], so signing out (which clears the session)
 * keeps the address. The password is never stored. If secure storage is unavailable the store quietly
 * does nothing, so sign-in never fails because of it.
 */
class RememberedEmailStore(private val prefs: SharedPreferences?) {

    fun get(): String? = prefs?.getString(KEY_EMAIL, null)?.takeIf { it.isNotBlank() }

    fun save(email: String) {
        val value = email.trim()
        if (value.isEmpty()) return
        prefs?.edit()?.putString(KEY_EMAIL, value)?.apply()
    }

    fun clear() {
        prefs?.edit()?.remove(KEY_EMAIL)?.apply()
    }

    companion object {
        const val PREFS_NAME = "eventqr_remembered_email"
        private const val KEY_EMAIL = "email"

        fun create(context: Context): RememberedEmailStore {
            val prefs = runCatching {
                EncryptedSharedPreferences.create(
                    PREFS_NAME,
                    MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            }.getOrNull()
            return RememberedEmailStore(prefs)
        }
    }
}
