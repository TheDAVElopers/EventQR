package com.thedavelopers.eventqr.features.attendee

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.session.SessionManager

open class AttendeeProfileActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        val sessionManager = SessionManager(this)
        findViewById<TextView>(R.id.txtProfileName).text =
            sessionManager.getFullName()?.takeIf { it.isNotBlank() } ?: "Attendee"
        findViewById<TextView>(R.id.txtProfileRole).text =
            sessionManager.getUserRole()?.lowercase()?.replaceFirstChar { it.titlecase() } ?: "Attendee"
        findViewById<TextView>(R.id.txtProfileEmail).text =
            sessionManager.getEmail()?.takeIf { it.isNotBlank() } ?: "No email saved"
        findViewById<Button>(R.id.btnEditProfile).setOnClickListener {
            startActivity(Intent(this, AttendeeEditProfileActivity::class.java))
        }
        findViewById<Button>(R.id.btnProfileLogout).setOnClickListener {
            sessionManager.clearSession()
            startActivity(
                Intent(this, com.thedavelopers.eventqr.SignIn::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            finish()
        }

        configureAttendeeBottomNav(AttendeeBottomNavItem.PROFILE)
    }
}

open class AttendeeEditProfileActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)
    }
}
