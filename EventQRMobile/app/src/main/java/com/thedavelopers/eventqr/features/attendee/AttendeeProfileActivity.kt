package com.thedavelopers.eventqr.features.attendee

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.features.dashboard.DashboardActivity
import com.thedavelopers.eventqr.features.registrations.RegistrationsCache
import com.thedavelopers.eventqr.features.users.model.dto.UserResponse
import com.thedavelopers.eventqr.ui.components.AttendeeNavItems
import com.thedavelopers.eventqr.ui.components.EventQrBottomNavBar
import com.thedavelopers.eventqr.ui.profile.UserProfileScreen
import com.thedavelopers.eventqr.ui.theme.EventQrTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class AttendeeProfileActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var repository: AttendeeRepository

    private val _user = MutableStateFlow<UserResponse?>(null)
    private val _isLoading = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager(this)
        repository = AttendeeRepository(this)

        if (!RoleMapper.isAtLeast(sessionManager.getUserRole(), AccountRole.ATTENDEE)) {
            Toast.makeText(this, "Access Denied", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContent {
            EventQrTheme {
                val user = _user.collectAsStateWithLifecycle().value
                val isLoading = _isLoading.collectAsStateWithLifecycle().value
                val errorMessage = _errorMessage.collectAsStateWithLifecycle().value

                val fullName = user?.fullName ?: sessionManager.getFullName().orEmpty().ifBlank { "Attendee" }
                val role = user?.role?.name ?: sessionManager.getUserRole().orEmpty()
                val roleDisplayName = RoleMapper.getDisplayName(role).ifBlank { "Attendee" }
                val email = user?.email ?: sessionManager.getEmail().orEmpty()
                val phone = user?.phoneNumber ?: sessionManager.getPhone()

                UserProfileScreen(
                    fullName = fullName,
                    roleDisplayName = roleDisplayName,
                    email = email,
                    phoneNumber = phone,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    onEditProfileClick = {
                        startActivity(Intent(this, AttendeeEditProfileActivity::class.java))
                    },
                    onTransactionsClick = {
                        startActivity(Intent(this, AttendeeTransactionsActivity::class.java))
                    },
                    onClaimedRewardsClick = {
                        startActivity(Intent(this, ClaimedRewardsActivity::class.java))
                    },
                    onEventRequestsClick = {
                        startActivity(Intent(this, MyEventRequestsActivity::class.java))
                    },
                    onChangePasswordClick = {
                        startActivity(com.thedavelopers.eventqr.core.navigation.AppNavigator.changePassword(this))
                    },
                    onSignOutConfirm = { performSignOut() },
                    onRetryClick = { loadProfile() },
                    bottomBar = {
                        EventQrBottomNavBar(
                            items = AttendeeNavItems,
                            selectedId = "profile",
                            onItemSelected = { id ->
                                when (id) {
                                    "home" -> {
                                        startActivity(Intent(this, DashboardActivity::class.java))
                                        finish()
                                    }
                                    "events" -> {
                                        startActivity(Intent(this, AttendeeEventsActivity::class.java))
                                        finish()
                                    }
                                    "registered" -> {
                                        startActivity(Intent(this, RegisteredEventsActivity::class.java))
                                        finish()
                                    }
                                    "rewards" -> {
                                        startActivity(Intent(this, AttendeeRewardsActivity::class.java))
                                        finish()
                                    }
                                    "profile" -> Unit
                                }
                            },
                        )
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadProfile()
    }

    private fun loadProfile() {
        _isLoading.value = true
        _errorMessage.value = null

        lifecycleScope.launch {
            when (val result = repository.getMyProfile()) {
                is NetworkResult.Success -> {
                    val user = result.data
                    sessionManager.updateProfile(user.fullName, user.phoneNumber, user.email)
                    sessionManager.saveRole(user.role)
                    _user.value = user
                    _isLoading.value = false
                }
                is NetworkResult.Error -> {
                    _isLoading.value = false
                    _errorMessage.value = result.message.ifBlank { "Unable to load profile." }
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun performSignOut() {
        RegistrationsCache.clear()
        sessionManager.clearSession()
        startActivity(
            Intent(this, com.thedavelopers.eventqr.features.auth.login.LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        finish()
    }
}
