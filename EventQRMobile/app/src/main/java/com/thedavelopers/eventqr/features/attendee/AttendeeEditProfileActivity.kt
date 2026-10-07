package com.thedavelopers.eventqr.features.attendee

import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.textfield.TextInputLayout
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.Validators
import kotlinx.coroutines.launch

class AttendeeEditProfileActivity : AppCompatActivity() {
    private lateinit var sessionManager: SessionManager
    private lateinit var repository: AttendeeRepository

    private lateinit var btnBack: ImageButton
    private lateinit var edtFullName: EditText
    private lateinit var edtEmail: EditText
    private lateinit var edtPhone: EditText
    private lateinit var tilPhone: TextInputLayout
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
        tilPhone = findViewById(R.id.tilPhone)
        cardError = findViewById(R.id.cardError)
        txtApiError = findViewById(R.id.txtApiError)
        btnRetryProfileLoad = findViewById(R.id.btnRetryProfileLoad)
        skeletonLoading = findViewById(R.id.skeletonLoading)
        swipeRefresh = findViewById(R.id.swipeRefreshEditProfile)
        layoutEditProfileContent = findViewById(R.id.layoutEditProfileContent)
        btnSaveChanges = findViewById(R.id.btnSaveChanges)
        txtEmptyHint = findViewById(R.id.txtEmptyHint)

        swipeRefresh.setColorSchemeResources(R.color.eventqr_purple)
        swipeRefresh.setOnRefreshListener { loadCurrentProfile() }
    }

    private fun bindActions() {
        btnBack.setOnClickListener { finish() }
        btnRetryProfileLoad.setOnClickListener { loadCurrentProfile() }

        btnSaveChanges.setOnClickListener { attemptSave() }

        findViewById<View>(R.id.btnChangePassword).setOnClickListener {
            startActivity(com.thedavelopers.eventqr.core.navigation.AppNavigator.changePassword(this))
        }

        configurePhoneInput()
        attachFormWatchers()
    }

    private fun attachFormWatchers() {
        edtFullName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(editable: Editable?) = onFormValueChanged()
        })
    }

    private fun onFormValueChanged() {
        clearApiError()
        clearFieldErrors()
        updateSaveButtonState()
    }

    private fun prefillFromSession() {
        edtFullName.setText(sessionManager.getFullName().orEmpty())
        edtEmail.setText(sessionManager.getEmail().orEmpty())
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
                    applyServerValue(edtFullName, user.fullName)
                    applyServerValue(edtEmail, user.email)
                    val phoneDigits = normalizePhoneDigits(user.phoneNumber.orEmpty())
                    applyServerValue(edtPhone, phoneDigits)

                    sessionManager.updateProfile(
                        fullName = user.fullName,
                        phone = user.phoneNumber,
                        email = user.email
                    )
                    sessionManager.saveRole(user.role)

                    txtEmptyHint.visibility = if (phoneDigits.isBlank()) View.VISIBLE else View.GONE
                    captureInitialSnapshot(user.fullName.trim(), user.email, phoneDigits)
                }

                is NetworkResult.Error -> showApiError(profileResult.message)
                else -> Unit
            }

            setLoadingState(false)
        }
    }

    private fun applyServerValue(field: EditText, serverValue: String) {
        if (field.hasFocus()) return
        if (field.text.toString() == serverValue) return
        field.setText(serverValue)
    }

    private fun attemptSave() {
        clearApiError()
        clearFieldErrors()

        if (!validateForm()) return
        if (!hasChanges()) return

        isSavingProfile = true
        updateSaveButtonState()

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

    private fun validateForm(): Boolean {
        val phone = sanitizePhone()
        if (phone.isBlank()) {
            edtPhone.error = "Phone number is required."
            return false
        }
        // Same rule as registration: the field holds normalized national digits and the
        // assembled E.164 value must satisfy Validators.isValidPhoneNumber.
        if (!Validators.isValidPhoneNumber("+63$phone")) {
            edtPhone.error = "Enter a valid 10-digit mobile number"
            return false
        }
        return true
    }

    private fun hasChanges(): Boolean {
        return sanitizeName() != initialFullName || sanitizePhone() != initialPhone
    }

    private fun captureInitialFormSnapshot() {
        captureInitialSnapshot(sanitizeName(), sanitizeEmail(), sanitizePhone())
    }

    private fun captureInitialSnapshot(name: String, email: String, phone: String) {
        initialFullName = name
        initialEmail = email
        initialPhone = phone
    }

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
                }
                onFormValueChanged()
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
        edtPhone.error = null
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
        edtPhone.isEnabled = !loading
        edtFullName.isEnabled = !loading
        updateSaveButtonState()
    }

    private fun updateSaveButtonState() {
        val canSave = !isLoadingProfile && !isSavingProfile && hasChanges()
        btnSaveChanges.isEnabled = canSave
        btnSaveChanges.text = if (isSavingProfile) "Saving..." else "Save Changes"
    }
}
