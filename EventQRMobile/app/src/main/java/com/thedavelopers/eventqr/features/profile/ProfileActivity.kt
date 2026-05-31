package com.thedavelopers.eventqr.features.profile

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.ProgressBar
import androidx.lifecycle.lifecycleScope
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import kotlinx.coroutines.launch
import java.util.Base64

class ProfileActivity : com.thedavelopers.eventqr.core.ui.BaseNavActivity() {
    private lateinit var sessionManager: com.thedavelopers.eventqr.core.session.SessionManager
    private lateinit var repository: com.thedavelopers.eventqr.features.attendee.AttendeeRepository
    private lateinit var imgProfileAvatar: ImageView
    private lateinit var imgProfileAvatarPlaceholder: View
    private lateinit var progressProfileLoading: ProgressBar
    private lateinit var txtProfileError: TextView
    private lateinit var btnProfileRetry: android.widget.Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        sessionManager = com.thedavelopers.eventqr.core.session.SessionManager(this)
        repository = com.thedavelopers.eventqr.features.attendee.AttendeeRepository(this)

        imgProfileAvatar = findViewById(R.id.imgProfileAvatar)
        imgProfileAvatarPlaceholder = findViewById(R.id.imgProfileAvatarPlaceholder)
        progressProfileLoading = findViewById(R.id.progressProfileLoading)
        txtProfileError = findViewById(R.id.txtProfileError)
        btnProfileRetry = findViewById(R.id.btnProfileRetry)

        btnProfileRetry.setOnClickListener { loadProfile() }

        val bottomNav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation) ?: findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.nav_view_container)
        setupBottomNavigation(bottomNav)
        updateBottomNavSelection(bottomNav, R.id.nav_profile)

        findViewById<android.widget.Button>(R.id.btnEditProfile)?.setOnClickListener {
            startActivity(android.content.Intent(this, com.thedavelopers.eventqr.features.attendee.AttendeeEditProfileActivity::class.java))
        }

        findViewById<android.widget.Button>(R.id.btnProfileLogout)?.setOnClickListener {
            sessionManager.clearSession()
            startActivity(android.content.Intent(this, com.thedavelopers.eventqr.features.auth.login.LoginActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        loadProfile()
    }

    private fun loadProfile() {
        setLoadingState(true)
        clearErrorState()
        renderProfile()
        
        lifecycleScope.launch {
            when (val result = repository.getMyProfile()) {
                is NetworkResult.Success -> {
                    val user = result.data
                    sessionManager.updateProfile(user.fullName, user.phoneNumber, user.email)
                    sessionManager.saveRole(user.role)
                    sessionManager.saveAvatarFileId(user.avatarFileId)
                    renderProfile()
                    renderAvatarFromStoredFile(user.avatarFileId)
                }
                is NetworkResult.Error -> showErrorState(result.message.ifBlank { "Unable to load profile." })
                else -> Unit
            }

            setLoadingState(false)
        }
    }

    private fun renderProfile() {
        findViewById<TextView>(R.id.txtProfileName).text = sessionManager.getFullName() ?: "User"
        findViewById<TextView>(R.id.txtProfileRole).text = com.thedavelopers.eventqr.core.util.RoleMapper.getDisplayName(sessionManager.getUserRole())
        findViewById<TextView>(R.id.txtProfileEmail).text = sessionManager.getEmail() ?: ""
        findViewById<TextView>(R.id.txtPhone).text = sessionManager.getPhone() ?: "N/A"
    }

    private fun renderAvatarFromStoredFile(avatarFileId: String?) {
        if (avatarFileId.isNullOrBlank()) {
            imgProfileAvatar.setImageDrawable(null)
            imgProfileAvatar.visibility = View.GONE
            imgProfileAvatarPlaceholder.visibility = View.VISIBLE
            return
        }

        lifecycleScope.launch {
            when (val fileResult = repository.getStoredFile(avatarFileId)) {
                is NetworkResult.Success -> {
                    val encoded = fileResult.data.contentBase64.orEmpty()
                    val bitmap = runCatching {
                        val bytes = Base64.getDecoder().decode(encoded)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }.onFailure {
                        Log.w("ProfileActivity", "Unable to decode profile avatar.", it)
                    }.getOrNull()

                    if (bitmap != null) {
                        imgProfileAvatar.setImageBitmap(bitmap)
                        imgProfileAvatar.visibility = View.VISIBLE
                        imgProfileAvatarPlaceholder.visibility = View.GONE
                    } else {
                        imgProfileAvatar.setImageDrawable(null)
                        imgProfileAvatar.visibility = View.GONE
                        imgProfileAvatarPlaceholder.visibility = View.VISIBLE
                    }
                }

                else -> {
                    imgProfileAvatar.setImageDrawable(null)
                    imgProfileAvatar.visibility = View.GONE
                    imgProfileAvatarPlaceholder.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun setLoadingState(loading: Boolean) {
        progressProfileLoading.visibility = if (loading) View.VISIBLE else View.GONE
        btnProfileRetry.visibility = View.GONE
        txtProfileError.visibility = View.GONE
    }

    private fun showErrorState(message: String) {
        txtProfileError.text = message
        txtProfileError.visibility = View.VISIBLE
        btnProfileRetry.visibility = View.VISIBLE
    }

    private fun clearErrorState() {
        txtProfileError.text = ""
        txtProfileError.visibility = View.GONE
        btnProfileRetry.visibility = View.GONE
    }
}
