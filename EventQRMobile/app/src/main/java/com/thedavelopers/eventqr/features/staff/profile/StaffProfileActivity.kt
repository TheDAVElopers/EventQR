package com.thedavelopers.eventqr.features.staff

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.features.staff.scanner.ScannerActivity
import kotlinx.coroutines.launch

open class StaffProfileActivity : AppCompatActivity() {
    private lateinit var sessionManager: SessionManager
    private lateinit var repository: com.thedavelopers.eventqr.features.attendee.AttendeeRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager(this)
        repository = com.thedavelopers.eventqr.features.attendee.AttendeeRepository(this)

        if (RoleMapper.normalizeRole(sessionManager.getUserRole()) != AccountRole.STAFF.name) {
            Toast.makeText(this, "Access Denied: Staff only", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContentView(R.layout.activity_profile)

        setupStaffBottomNav()

        findViewById<Button>(R.id.btnProfileLogout).setOnClickListener {
            sessionManager.clearSession()
            startActivity(Intent(this, com.thedavelopers.eventqr.features.auth.login.LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
        }

        findViewById<Button>(R.id.btnEditProfile)?.setOnClickListener {
            startActivity(Intent(this, com.thedavelopers.eventqr.features.attendee.AttendeeEditProfileActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        loadProfile()
    }

    private fun loadProfile() {
        renderProfile()

        kotlinx.coroutines.MainScope().launch {
            when (val result = repository.getMyProfile()) {
                is NetworkResult.Success -> {
                    val user = result.data
                    sessionManager.updateProfile(user.fullName, user.phoneNumber)
                    renderProfile()
                }
                else -> Unit
            }
        }
    }

    private fun renderProfile() {
        findViewById<TextView>(R.id.txtProfileName).text = sessionManager.getFullName() ?: "Staff User"
        findViewById<TextView>(R.id.txtProfileRole).text = RoleMapper.getDisplayName(sessionManager.getUserRole())
        findViewById<TextView>(R.id.txtProfileEmail).text = sessionManager.getEmail() ?: "staff@eventqr.com"
        findViewById<TextView>(R.id.txtPhone).text = sessionManager.getPhone() ?: "N/A"
    }

    private fun setupStaffBottomNav() {
        findViewById<ImageView>(R.id.imgNavDashboard)?.apply {
            setImageResource(R.drawable.ic_nav_home)
            background = getDrawable(R.drawable.bg_nav_icon_inactive)
            imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.BLACK)
        }
        findViewById<TextView>(R.id.txtNavDashboard)?.apply {
            text = "Dashboard"
            setTypeface(null, android.graphics.Typeface.NORMAL)
        }
        findViewById<View>(R.id.navDashboard)?.setOnClickListener {
            startActivity(Intent(this, StaffDashboardActivity::class.java))
            finish()
        }

        findViewById<ImageView>(R.id.imgNavEvents)?.apply {
            setImageResource(R.drawable.ic_qr_scan)
            background = getDrawable(R.drawable.bg_nav_icon_inactive)
            imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.BLACK)
        }
        findViewById<TextView>(R.id.txtNavEvents)?.apply {
            text = "Scan QR"
            setTypeface(null, android.graphics.Typeface.NORMAL)
        }
        findViewById<View>(R.id.navEvents)?.setOnClickListener {
            startActivity(Intent(this, ScannerActivity::class.java))
            finish()
        }

        findViewById<ImageView>(R.id.imgNavRewards)?.apply {
            setImageResource(R.drawable.ic_file)
            background = getDrawable(R.drawable.bg_nav_icon_inactive)
            imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.BLACK)
        }
        findViewById<TextView>(R.id.txtNavRewards)?.apply {
            text = "Logs"
            setTypeface(null, android.graphics.Typeface.NORMAL)
        }
        findViewById<View>(R.id.navRewards)?.setOnClickListener {
            startActivity(Intent(this, StaffTransactionsActivity::class.java))
            finish()
        }

        findViewById<ImageView>(R.id.imgNavProfile)?.apply {
            setImageResource(R.drawable.ic_nav_profile)
            background = getDrawable(R.drawable.bg_nav_icon_active)
            imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
        }
        findViewById<TextView>(R.id.txtNavProfile)?.apply {
            text = "Profile"
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
    }
}
