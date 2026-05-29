package com.thedavelopers.eventqr.features.attendee

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.RoleMapper
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

open class AttendeeProfileActivity : AppCompatActivity() {
    private lateinit var sessionManager: SessionManager
    private lateinit var repository: AttendeeRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        sessionManager = SessionManager(this)
        repository = AttendeeRepository(this)
        
        findViewById<Button>(R.id.btnEditProfile).setOnClickListener {
            startActivity(Intent(this, AttendeeEditProfileActivity::class.java))
        }
        findViewById<Button>(R.id.btnProfileLogout).setOnClickListener {
            sessionManager.clearSession()
            startActivity(
                Intent(this, com.thedavelopers.eventqr.features.auth.login.LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            finish()
        }

        configureAttendeeBottomNav(AttendeeBottomNavItem.PROFILE)
    }

    override fun onResume() {
        super.onResume()
        loadProfile()
    }

    private fun loadProfile() {
        // Initial sync from session
        renderProfile()

        // Fresh fetch from backend
        lifecycleScope.launch {
            when (val result = repository.getMyProfile()) {
                is com.thedavelopers.eventqr.core.api.NetworkResult.Success -> {
                    val user = result.data
                    sessionManager.updateProfile(user.fullName, user.phoneNumber)
                    // If login didn't provide role/email or it changed, update it too
                    // Note: SessionManager doesn't have an update for these yet, but render will use what's there
                    renderProfile()
                }
                else -> Unit
            }
        }
    }

    private fun renderProfile() {
        findViewById<TextView>(R.id.txtProfileName).text =
            sessionManager.getFullName()?.takeIf { it.isNotBlank() } ?: "Attendee"
        findViewById<TextView>(R.id.txtProfileRole).text =
            RoleMapper.getDisplayName(sessionManager.getUserRole())
        findViewById<TextView>(R.id.txtProfileEmail).text =
            sessionManager.getEmail()?.takeIf { it.isNotBlank() } ?: "No email saved"
        findViewById<TextView>(R.id.txtPhone).text =
            sessionManager.getPhone()?.takeIf { it.isNotBlank() } ?: "No Phone Number saved"
    }
}

open class AttendeeEditProfileActivity : AppCompatActivity() {
    private lateinit var sessionManager: SessionManager
    private lateinit var repository: AttendeeRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        sessionManager = SessionManager(this)
        repository = AttendeeRepository(this)

        val edtFullName = findViewById<android.widget.EditText>(R.id.edtFullName)
        val edtEmail = findViewById<android.widget.EditText>(R.id.edtEmail)
        val edtPhone = findViewById<android.widget.EditText>(R.id.edtPhone)
        val btnSaveChanges = findViewById<android.widget.Button>(R.id.btnSaveChanges)

        edtFullName.setText(sessionManager.getFullName())
        edtEmail.setText(sessionManager.getEmail())
        edtPhone.setText(sessionManager.getPhone())

        findViewById<TextView>(R.id.btnBack).setOnClickListener {
            finish()
        }
        findViewById<android.view.View>(R.id.btnBackImage).setOnClickListener {
            finish()
        }

        btnSaveChanges.setOnClickListener {
            val fullName = edtFullName.text.toString()
            val phone = edtPhone.text.toString()

            if (fullName.isBlank()) {
                edtFullName.error = "Name cannot be empty"
                return@setOnClickListener
            }

            btnSaveChanges.isEnabled = false
            btnSaveChanges.text = "Saving..."

            lifecycleScope.launch {
                val result = repository.updateProfile(fullName, phone)
                btnSaveChanges.isEnabled = true
                btnSaveChanges.text = "Save Changes"

                when (result) {
                    is com.thedavelopers.eventqr.core.api.NetworkResult.Success -> {
                        sessionManager.updateProfile(fullName, phone)
                        Toast.makeText(this@AttendeeEditProfileActivity, "Profile updated", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    is com.thedavelopers.eventqr.core.api.NetworkResult.Error -> {
                        Toast.makeText(this@AttendeeEditProfileActivity, result.message, Toast.LENGTH_LONG).show()
                    }
                    else -> Unit
                }
            }
        }
    }
}
