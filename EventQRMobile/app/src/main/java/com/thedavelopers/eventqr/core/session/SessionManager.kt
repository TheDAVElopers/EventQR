package com.thedavelopers.eventqr.core.session

import android.content.Context
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.features.auth.model.dto.LoginResponse

class SessionManager(context: Context) {
    private val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveLoginResponse(loginResponse: LoginResponse) {
        sharedPreferences.edit()
            .putString(KEY_AUTH_TOKEN, loginResponse.accessToken)
            .putString(KEY_USER_ID, loginResponse.userId.toString())
            .putString(KEY_EMAIL, loginResponse.email)
            .putString(KEY_PHONE, loginResponse.phone)
            .putString(KEY_FULL_NAME, loginResponse.fullName)
            .putString(KEY_ROLE, loginResponse.role?.name)
            .apply()
    }

    fun saveRole(role: AccountRole?) {
        sharedPreferences.edit().putString(KEY_ROLE, role?.name).apply()
    }

    fun saveAvatarFileId(avatarFileId: String?) {
        sharedPreferences.edit()
            .putString(KEY_AVATAR_FILE_ID, avatarFileId)
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

    fun setAvatarLocalPath(localPath: String?) {
        sharedPreferences.edit()
            .putString(KEY_AVATAR_LOCAL_PATH, localPath)
            .apply()
    }

    fun clearSession() {
        sharedPreferences.edit().clear().apply()
    }

    fun getAuthToken(): String? = sharedPreferences.getString(KEY_AUTH_TOKEN, null)

    fun getUserId(): String? = sharedPreferences.getString(KEY_USER_ID, null)

    fun getUserRole(): String? = sharedPreferences.getString(KEY_ROLE, null)

    fun getEmail(): String? = sharedPreferences.getString(KEY_EMAIL, null)

    fun getPhone(): String? = sharedPreferences.getString(KEY_PHONE, null)

    fun getFullName(): String? = sharedPreferences.getString(KEY_FULL_NAME, null)

    fun getAvatarLocalPath(): String? = sharedPreferences.getString(KEY_AVATAR_LOCAL_PATH, null)

    fun getAvatarFileId(): String? = sharedPreferences.getString(KEY_AVATAR_FILE_ID, null)

    fun hasUsableToken(): Boolean {
        return getAuthToken().orEmpty().isNotBlank()
    }

    companion object {
        const val PREFS_NAME = "eventqr_session"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_ROLE = "role"
        private const val KEY_EMAIL = "email"
        private const val KEY_PHONE = "phone"
        private const val KEY_FULL_NAME = "full_name"
        private const val KEY_AVATAR_LOCAL_PATH = "avatar_local_path"
        private const val KEY_AVATAR_FILE_ID = "avatar_file_id"
    }
}
