package com.thedavelopers.eventqr.features.attendee

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.session.SessionLogout
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.features.users.model.dto.UserResponse
import com.thedavelopers.eventqr.ui.theme.applyEventQrTopInsetPadding
import kotlinx.coroutines.launch

class AttendeeProfileActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var repository: AttendeeRepository
    private lateinit var txtProfileName: TextView
    private lateinit var txtProfileRole: TextView
    private lateinit var skeletonLoading: View
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var layoutProfileMenu: View
    private lateinit var txtProfileError: TextView
    private lateinit var btnProfileRetry: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager(this)
        repository = AttendeeRepository(this)

        if (!RoleMapper.isAtLeast(sessionManager.getUserRole(), AccountRole.ATTENDEE)) {
            Toast.makeText(this, "Access Denied", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContentView(R.layout.activity_attendee_profile)
        findViewById<View>(R.id.headerProfile).applyEventQrTopInsetPadding()

        txtProfileName = findViewById(R.id.txtProfileName)
        txtProfileRole = findViewById(R.id.txtProfileRole)
        skeletonLoading = findViewById(R.id.skeletonLoading)
        swipeRefresh = findViewById(R.id.swipeRefreshProfile)
        layoutProfileMenu = findViewById(R.id.layoutProfileMenu)
        txtProfileError = findViewById(R.id.txtProfileError)
        btnProfileRetry = findViewById(R.id.btnProfileRetry)

        swipeRefresh.setColorSchemeResources(R.color.eventqr_purple)
        swipeRefresh.setOnRefreshListener { loadProfile() }

        btnProfileRetry.setOnClickListener { loadProfile() }

        findViewById<View>(R.id.cardEditProfile).setOnClickListener {
            startActivity(Intent(this, AttendeeEditProfileActivity::class.java))
        }
        findViewById<View>(R.id.cardTransactionHistory).setOnClickListener {
            startActivity(Intent(this, AttendeeTransactionsActivity::class.java))
        }
        findViewById<View>(R.id.cardClaimedRewards).setOnClickListener {
            startActivity(Intent(this, ClaimedRewardsActivity::class.java))
        }
        findViewById<View>(R.id.cardMyEventRequests).setOnClickListener {
            startActivity(Intent(this, MyEventRequestsActivity::class.java))
        }
        findViewById<View>(R.id.cardSignOut).setOnClickListener {
            showSignOutConfirmation()
        }

        configureAttendeeBottomNav(AttendeeBottomNavItem.PROFILE)
    }

    private fun showSignOutConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Sign Out")
            .setMessage("Are you sure you want to sign out?")
            .setPositiveButton("Sign Out") { dialog, _ ->
                dialog.dismiss()
                performSignOut()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun performSignOut() {
        findViewById<View>(R.id.cardSignOut).isEnabled = false
        lifecycleScope.launch {
            // Revokes the token on the server first, then clears local session state.
            SessionLogout.signOut(this@AttendeeProfileActivity)
            startActivity(
                Intent(this@AttendeeProfileActivity, com.thedavelopers.eventqr.features.auth.login.LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
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

        renderProfile(null)

        lifecycleScope.launch {
            when (val result = repository.getMyProfile()) {
                is NetworkResult.Success -> {
                    val user = result.data
                    sessionManager.updateProfile(user.fullName, user.phoneNumber, user.email)
                    sessionManager.saveRole(user.role)
                    renderProfile(user)
                    clearErrorState()
                }
                is NetworkResult.Error -> showErrorState(result.message.ifBlank { "Unable to load profile." })
                else -> Unit
            }

            setLoadingState(false)
        }
    }

    private fun renderProfile(user: UserResponse? = null) {
        txtProfileName.text = user?.fullName ?: sessionManager.getFullName().orEmpty()
        txtProfileRole.text = (user?.role?.name ?: sessionManager.getUserRole())
            ?.takeIf { it.isNotBlank() }
            ?.let { RoleMapper.getDisplayName(it) }
            .orEmpty()

        val name = user?.fullName ?: sessionManager.getFullName().orEmpty()
        findViewById<TextView>(R.id.txtProfileInitial)?.text =
            name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

        findViewById<TextView>(R.id.txtProfileDetailName)?.text = user?.fullName
        findViewById<TextView>(R.id.txtProfileDetailEmail)?.text = user?.email
        findViewById<TextView>(R.id.txtProfileDetailPhone)?.text = user?.phoneNumber ?: "—"
    }

    private fun setLoadingState(loading: Boolean) {
        if (!swipeRefresh.isRefreshing) {
            skeletonLoading.visibility = if (loading) View.VISIBLE else View.GONE
        }
        if (loading) {
            layoutProfileMenu.visibility = View.GONE
            btnProfileRetry.visibility = View.GONE
            txtProfileError.visibility = View.GONE
        } else {
            swipeRefresh.isRefreshing = false
            layoutProfileMenu.visibility = View.VISIBLE
        }
    }

    private fun showErrorState(message: String) {
        skeletonLoading.visibility = View.GONE
        layoutProfileMenu.visibility = View.VISIBLE
        txtProfileError.text = message
        txtProfileError.visibility = View.VISIBLE
        btnProfileRetry.visibility = View.VISIBLE
    }

    private fun clearErrorState() {
        txtProfileError.visibility = View.GONE
        btnProfileRetry.visibility = View.GONE
    }
}
