package com.thedavelopers.eventqr.core.session

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.features.auth.model.dto.LoginResponse

class SessionManager(context: Context) : TokenStore {
    private val sharedPreferences: SharedPreferences = sharedPrefsFor(context.applicationContext ?: context)

    fun saveLoginResponse(loginResponse: LoginResponse) {
        sharedPreferences.edit()
            .putString(KEY_AUTH_TOKEN, loginResponse.accessToken)
            .apply { loginResponse.refreshToken?.let { putString(KEY_REFRESH_TOKEN, it) } }
            .putString(KEY_USER_ID, loginResponse.userId.toString())
            .putString(KEY_EMAIL, loginResponse.email)
            .putString(KEY_PHONE, loginResponse.phone)
            .putString(KEY_FULL_NAME, loginResponse.fullName)
            .putString(KEY_ROLE, loginResponse.role?.name)
            .apply()
    }

    fun updateProfile(fullName: String, phone: String?) {
        sharedPreferences.edit()
            .putString(KEY_FULL_NAME, fullName)
            .putString(KEY_PHONE, phone)
            .apply()
    }

    fun updateProfile(fullName: String, phone: String?, email: String?) {
        sharedPreferences.edit()
            .putString(KEY_FULL_NAME, fullName)
            .putString(KEY_PHONE, phone)
            .putString(KEY_EMAIL, email)
            .apply()
    }

    fun saveRole(role: AccountRole?) {
        sharedPreferences.edit()
            .putString(KEY_ROLE, role?.name)
            .apply()
    }

    fun clearSession() {
        sharedPreferences.edit().clear().apply()
    }

    fun getAuthToken(): String? = sharedPreferences.getString(KEY_AUTH_TOKEN, null)

    override fun getAccessToken(): String? = getAuthToken()

    override fun getRefreshToken(): String? = sharedPreferences.getString(KEY_REFRESH_TOKEN, null)

    override fun saveTokens(accessToken: String, refreshToken: String?) {
        sharedPreferences.edit()
            .putString(KEY_AUTH_TOKEN, accessToken)
            .apply { refreshToken?.let { putString(KEY_REFRESH_TOKEN, it) } }
            .apply()
    }

    override fun clear() = clearSession()

    fun getUserId(): String? = sharedPreferences.getString(KEY_USER_ID, null)

    fun getUserRole(): String? = sharedPreferences.getString(KEY_ROLE, null)

    fun getEmail(): String? = sharedPreferences.getString(KEY_EMAIL, null)

    fun getPhone(): String? = sharedPreferences.getString(KEY_PHONE, null)

    fun getFullName(): String? = sharedPreferences.getString(KEY_FULL_NAME, null)

    fun hasUsableToken(): Boolean {
        return getAuthToken().orEmpty().isNotBlank()
    }

    companion object {
        private class Holder(val context: Context, val prefs: SharedPreferences)

        @Volatile
        private var holder: Holder? = null

        /**
         * Building EncryptedSharedPreferences (Keystore + Tink keyset) is the slow part of cold start, and used to
         * be repeated by every SessionManager (landing, API client, auth repository, refresher, dashboard). Build it
         * once per application context and share it. [warmUp] lets Application.onCreate do it off the main thread.
         */
        private fun sharedPrefsFor(appContext: Context): SharedPreferences {
            holder?.takeIf { it.context === appContext }?.let { return it.prefs }
            return synchronized(this) {
                holder?.takeIf { it.context === appContext }?.prefs
                    ?: create(appContext).also { holder = Holder(appContext, it) }
            }
        }

        fun warmUp(context: Context) {
            val appContext = context.applicationContext ?: context
            Thread({ runCatching { sharedPrefsFor(appContext) } }, "session-warmup").start()
        }

        private fun create(context: Context): SharedPreferences {
            // Stable security-crypto 1.0.0 API. getOrCreate(AES256_GCM_SPEC) uses the same
            // Keystore alias as the alpha MasterKey.Builder default, so sessions saved by
            // earlier builds stay readable.
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            return EncryptedSharedPreferences.create(
                PREFS_NAME,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }

        const val PREFS_NAME = "eventqr_session"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_ROLE = "role"
        private const val KEY_EMAIL = "email"
        private const val KEY_PHONE = "phone"
        private const val KEY_FULL_NAME = "full_name"
        private const val KEY_AVATAR_LOCAL_PATH = "avatar_local_path"
    }
}