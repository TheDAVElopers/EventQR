package com.thedavelopers.eventqr.features.attendee

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import com.google.android.material.appbar.MaterialToolbar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.features.registrations.RegistrationsCache
import com.thedavelopers.eventqr.features.users.model.dto.UserResponse
import kotlinx.coroutines.launch

private const val TAG = "AttendeeProfile"

open class AttendeeProfileActivity : AppCompatActivity() {
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
        setContentView(R.layout.activity_profile)

        sessionManager = SessionManager(this)
        repository = AttendeeRepository(this)

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

        // EventQR - UI safeguard beyond SRS/SDD explicit spec (no confirm-dialog requirement stated for sign out)
        findViewById<Button>(R.id.btnProfileLogout).setOnClickListener {
            showSignOutConfirmation()
        }

        configureAttendeeBottomNav(AttendeeBottomNavItem.PROFILE)
    }

    private fun showSignOutConfirmation() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
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
        RegistrationsCache.clear()
        sessionManager.clearSession()
        startActivity(
            Intent(this, com.thedavelopers.eventqr.features.auth.login.LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
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

        // Initial avatar
        val name = user?.fullName ?: sessionManager.getFullName().orEmpty()
        findViewById<TextView>(R.id.txtProfileInitial)?.text =
            name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

        // Profile detail rows
        findViewById<TextView>(R.id.txtProfileDetailName)?.text = user?.fullName
        findViewById<TextView>(R.id.txtProfileDetailEmail)?.text = user?.email
        findViewById<TextView>(R.id.txtProfileDetailPhone)?.text = user?.phoneNumber ?: "\u2014"
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

open class AttendeeEditProfileActivity : AppCompatActivity() {
    private lateinit var sessionManager: SessionManager
    private lateinit var repository: AttendeeRepository

    private lateinit var btnBack: MaterialToolbar
    private lateinit var edtFullName: EditText
    private lateinit var edtEmail: EditText
    private lateinit var edtPhone: EditText
    private lateinit var layoutPhoneField: View
    private lateinit var txtPhoneError: TextView
    private lateinit var cardError: View
    private lateinit var txtApiError: TextView
    private lateinit var btnRetryProfileLoad: Button
    private lateinit var skeletonLoading: View
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var layoutEditProfileContent: View
    private lateinit var btnSaveChanges: Button
    private lateinit var txtEmptyHint: TextView

    private var initialFullName: String = ""
    private var initialEmail: String = ""
    private var initialPhone: String = ""

    private var isLoadingProfile: Boolean = false
    private var isSavingProfile: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        sessionManager = SessionManager(this)
        repository = AttendeeRepository(this)

        bindViews()
        bindActions()
        prefillFromSession()
        loadCurrentProfile()
    }

    private fun bindViews() {
        btnBack = findViewById(R.id.toolbarEditProfile)
        edtFullName = findViewById(R.id.edtFullName)
        edtEmail = findViewById(R.id.edtEmail)
        edtPhone = findViewById(R.id.edtPhone)
        layoutPhoneField = findViewById(R.id.layoutPhoneField)
        txtPhoneError = findViewById(R.id.txtPhoneError)
        cardError = findViewById(R.id.cardError)
        txtApiError = findViewById(R.id.txtApiError)
        btnRetryProfileLoad = findViewById(R.id.btnRetryProfileLoad)
        skeletonLoading = findViewById(R.id.skeletonLoading)
        swipeRefresh = findViewById(R.id.swipeRefreshEditProfile)
        layoutEditProfileContent = findViewById(R.id.layoutEditProfileContent)
        btnSaveChanges = findViewById(R.id.btnSaveChanges)
        txtEmptyHint = findViewById(R.id.txtEmptyHint)

        swipeRefresh.setColorSchemeResources(R.color.accent_signal)
        swipeRefresh.setOnRefreshListener { loadCurrentProfile() }
    }

    private fun bindActions() {
        btnBack.setNavigationOnClickListener { finish() }
        btnRetryProfileLoad.setOnClickListener { loadCurrentProfile() }

        btnSaveChanges.setOnClickListener { attemptSave() }

        findViewById<View>(R.id.btnChangePassword).setOnClickListener {
            startActivity(com.thedavelopers.eventqr.core.navigation.AppNavigator.changePassword(this))
        }

        // EventQR - phone update only, matches Register screen prefix/validation pattern
        configurePhoneInput()

        val formWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                clearApiError()
                clearFieldErrors()
                updateSaveButtonState()
            }
        }

        edtPhone.addTextChangedListener(formWatcher)
    }

    private fun prefillFromSession() {
        edtFullName.setText(sessionManager.getFullName().orEmpty())
        edtEmail.setText(sessionManager.getEmail().orEmpty())
        // EventQR - phone update only, matches Register screen prefix/validation pattern
        val rawPhone = sessionManager.getPhone().orEmpty()
        edtPhone.setText(normalizePhoneDigits(rawPhone))

        captureInitialFormSnapshot()
        updateSaveButtonState()
    }

    private fun loadCurrentProfile() {
        setLoadingState(true)
        lifecycleScope.launch {
            when (val profileResult = repository.getMyProfile()) {
                is NetworkResult.Success -> {
                    val user = profileResult.data
                    edtFullName.setText(user.fullName)
                    edtEmail.setText(user.email)
                    // EventQR - phone update only, matches Register screen prefix/validation pattern
                    val phoneDigits = normalizePhoneDigits(user.phoneNumber.orEmpty())
                    edtPhone.setText(phoneDigits)

                    sessionManager.updateProfile(
                        fullName = user.fullName,
                        phone = user.phoneNumber,
                        email = user.email
                    )
                    sessionManager.saveRole(user.role)

                    // Show empty hint if phone is not set
                    txtEmptyHint.visibility = if (phoneDigits.isBlank()) View.VISIBLE else View.GONE
                }

                is NetworkResult.Error -> showApiError(profileResult.message)
                else -> Unit
            }

            captureInitialFormSnapshot()
            setLoadingState(false)
        }
    }

    private fun attemptSave() {
        clearApiError()
        clearFieldErrors()

        if (!validateForm()) return

        if (!hasChanges()) return

        isSavingProfile = true
        updateSaveButtonState()

        // EventQR - name/email locked (display-only), only phone is updatable
        val fullName = sanitizeName()
        val phoneDigits = sanitizePhone()
        val phone = if (phoneDigits.length == 10) "+63$phoneDigits" else null

        lifecycleScope.launch {
            when (val updateResult = repository.updateProfile(fullName, phone)) {
                is NetworkResult.Success -> {
                    sessionManager.updateProfile(fullName, phone, initialEmail)
                    captureInitialFormSnapshot()
                    Toast.makeText(this@AttendeeEditProfileActivity, "Profile updated successfully.", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }

                is NetworkResult.Error -> showApiError(updateResult.message)
                else -> Unit
            }

            isSavingProfile = false
            updateSaveButtonState()
        }
    }

    // EventQR - name/email locked (display-only, always valid from profile), only phone needs validation
    private fun validateForm(): Boolean {
        val phone = sanitizePhone()
        if (phone.isBlank()) {
            layoutPhoneField.background = ContextCompat.getDrawable(this, R.drawable.bg_phone_input_error)
            txtPhoneError.text = "Phone number is required."
            txtPhoneError.visibility = View.VISIBLE
            return false
        }
        if (phone.length != 10 || !phone.all { it.isDigit() }) {
            layoutPhoneField.background = ContextCompat.getDrawable(this, R.drawable.bg_phone_input_error)
            txtPhoneError.text = "Enter a valid 10-digit mobile number"
            txtPhoneError.visibility = View.VISIBLE
            return false
        }
        return true
    }

    // EventQR - name/email locked (display-only), only phone digits are updatable
    private fun hasChanges(): Boolean {
        return sanitizePhone() != initialPhone
    }

    private fun captureInitialFormSnapshot() {
        initialFullName = sanitizeName()
        initialEmail = sanitizeEmail()
        initialPhone = sanitizePhone()
    }

    // EventQR - phone update only, matches Register screen prefix/validation pattern
    private fun configurePhoneInput() {
        edtPhone.inputType = InputType.TYPE_CLASS_NUMBER
        edtPhone.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(editable: Editable?) {
                val current = editable ?: return
                val normalized = normalizePhoneDigits(current.toString())
                if (normalized != current.toString()) {
                    current.replace(0, current.length, normalized)
                    layoutPhoneField.background = ContextCompat.getDrawable(this@AttendeeEditProfileActivity, R.drawable.bg_phone_input)
                    txtPhoneError.visibility = View.GONE
                }
            }
        })
    }

    private fun normalizePhoneDigits(input: String): String {
        var digits = input.filter { it.isDigit() }
        while (digits.length > 10 && digits.startsWith("0")) {
            digits = digits.removePrefix("0")
        }
        while (digits.length > 10 && digits.startsWith("63")) {
            digits = digits.removePrefix("63")
        }
        if (digits.startsWith("0")) {
            digits = digits.removePrefix("0")
        }
        return digits.take(10)
    }

    private fun sanitizeName(): String = edtFullName.text.toString().trim()

    private fun sanitizeEmail(): String = edtEmail.text.toString().trim()

    private fun sanitizePhone(): String = edtPhone.text.toString().trim()

    private fun clearFieldErrors() {
        edtFullName.error = null
        edtEmail.error = null
        layoutPhoneField.background = ContextCompat.getDrawable(this@AttendeeEditProfileActivity, R.drawable.bg_phone_input)
        txtPhoneError.visibility = View.GONE
    }

    private fun showApiError(message: String) {
        skeletonLoading.visibility = View.GONE
        layoutEditProfileContent.visibility = View.VISIBLE
        txtApiError.text = message
        cardError.visibility = View.VISIBLE
        btnRetryProfileLoad.visibility = View.VISIBLE
    }

    private fun clearApiError() {
        txtApiError.text = ""
        cardError.visibility = View.GONE
        btnRetryProfileLoad.visibility = View.GONE
    }

    private fun setLoadingState(loading: Boolean) {
        isLoadingProfile = loading
        if (!swipeRefresh.isRefreshing) {
            skeletonLoading.visibility = if (loading) View.VISIBLE else View.GONE
        }
        if (loading) {
            layoutEditProfileContent.visibility = View.GONE
            txtEmptyHint.visibility = View.GONE
        } else {
            swipeRefresh.isRefreshing = false
            layoutEditProfileContent.visibility = View.VISIBLE
        }

        // EventQR - name/email locked (display-only), only phone responds to loading state
        edtPhone.isEnabled = !loading

        updateSaveButtonState()
    }

    private fun updateSaveButtonState() {
        val canSave = !isLoadingProfile && !isSavingProfile && hasChanges()
        btnSaveChanges.isEnabled = canSave
        btnSaveChanges.text = if (isSavingProfile) "Saving..." else "Save Changes"
    }
}
