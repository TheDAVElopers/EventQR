package com.thedavelopers.eventqr.features.attendee

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.Validators
import kotlinx.coroutines.launch

open class AttendeeRegistrationActivity : AppCompatActivity(), RegistrationContract.View {
    private lateinit var presenter: RegistrationPresenter
    private lateinit var repository: AttendeeRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var eventId: String
    private lateinit var eventCategoryText: TextView
    private lateinit var eventTitleText: TextView
    private lateinit var eventDateTimeVenueText: TextView
    private lateinit var fullNameInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var phoneInput: EditText
    private lateinit var termsCheckbox: CheckBox
    private lateinit var submitButton: Button

    private val userEditedFields = mutableMapOf(
        FIELD_FULL_NAME to false,
        FIELD_EMAIL to false,
        FIELD_PHONE to false,
    )
    private var suppressEditTracking = false
    private var isLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_registration)

        sessionManager = SessionManager(this)
        repository = AttendeeRepository(this)
        presenter = RegistrationPresenter(this, repository)
        eventId = intent.getStringExtra(EXTRA_EVENT_ID).orEmpty()

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        eventCategoryText = findViewById(R.id.txtEventCategory)
        eventTitleText = findViewById(R.id.txtEventTitle)
        eventDateTimeVenueText = findViewById(R.id.txtEventDateTimeVenue)
        fullNameInput = findViewById(R.id.edtRegistrationFullName)
        emailInput = findViewById(R.id.edtRegistrationEmail)
        phoneInput = findViewById(R.id.edtRegistrationPhone)
        termsCheckbox = findViewById(R.id.chkRegistrationTerms)
        submitButton = findViewById(R.id.btnSubmitRegistration)

        restoreState(savedInstanceState)
        bindEventSummary()
        attachInputTracking()
        applyInitialPrefill()
        loadLatestProfilePrefill()

        termsCheckbox.setOnCheckedChangeListener { _, _ -> updateSubmitButtonState() }
        submitButton.setOnClickListener { submitRegistration() }

        updateSubmitButtonState()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_FULL_NAME_EDITED, userEditedFields[FIELD_FULL_NAME] == true)
        outState.putBoolean(STATE_EMAIL_EDITED, userEditedFields[FIELD_EMAIL] == true)
        outState.putBoolean(STATE_PHONE_EDITED, userEditedFields[FIELD_PHONE] == true)
        outState.putBoolean(STATE_TERMS_CHECKED, termsCheckbox.isChecked)
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }

    override fun showLoading(isLoading: Boolean) {
        this.isLoading = isLoading
        submitButton.text = if (isLoading) "Submitting..." else "Confirm Registration"
        updateSubmitButtonState()
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showFieldError(field: String, message: String?) {
        when (field) {
            "fullName" -> fullNameInput.error = message
            "email" -> emailInput.error = message
            "phone" -> phoneInput.error = message
        }
    }

    override fun openQr(registrationId: String, qrCredentialId: String) {
        startActivity(
            Intent(this, AttendeeQrCredentialActivity::class.java)
                .putExtra(EXTRA_REGISTRATION_ID, registrationId)
                .putExtra(EXTRA_QR_CREDENTIAL_ID, qrCredentialId)
        )
        finish()
    }

    private fun restoreState(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) return

        userEditedFields[FIELD_FULL_NAME] = savedInstanceState.getBoolean(STATE_FULL_NAME_EDITED, false)
        userEditedFields[FIELD_EMAIL] = savedInstanceState.getBoolean(STATE_EMAIL_EDITED, false)
        userEditedFields[FIELD_PHONE] = savedInstanceState.getBoolean(STATE_PHONE_EDITED, false)
        termsCheckbox.isChecked = savedInstanceState.getBoolean(STATE_TERMS_CHECKED, false)
    }

    private fun bindEventSummary() {
        val category = intent.getStringExtra(EXTRA_EVENT_CATEGORY).orEmpty().ifBlank { "Event" }
        val title = intent.getStringExtra(EXTRA_EVENT_TITLE).orEmpty().ifBlank { "Register for Event" }
        val date = intent.getStringExtra(EXTRA_EVENT_START).orEmpty().ifBlank { "Date to be announced" }
        val venue = intent.getStringExtra(EXTRA_EVENT_LOCATION).orEmpty().ifBlank { "Venue to be announced" }

        eventCategoryText.text = category
        eventTitleText.text = title
        eventDateTimeVenueText.text = "$date · $venue"
    }

    private fun attachInputTracking() {
        trackUserEdits(fullNameInput, FIELD_FULL_NAME)
        trackUserEdits(emailInput, FIELD_EMAIL)
        trackUserEdits(phoneInput, FIELD_PHONE)
    }

    private fun trackUserEdits(editText: EditText, fieldKey: String) {
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!suppressEditTracking) {
                    userEditedFields[fieldKey] = true
                }
                updateSubmitButtonState()
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun applyInitialPrefill() {
        val fullName = intent.getStringExtra(EXTRA_PREFILL_FULL_NAME).orEmpty().ifBlank { sessionManager.getFullName().orEmpty() }
        val email = intent.getStringExtra(EXTRA_PREFILL_EMAIL).orEmpty().ifBlank { sessionManager.getEmail().orEmpty() }
        val phone = intent.getStringExtra(EXTRA_PREFILL_PHONE).orEmpty().ifBlank { sessionManager.getPhone().orEmpty() }

        applyPrefill(fullNameInput, FIELD_FULL_NAME, fullName)
        applyPrefill(emailInput, FIELD_EMAIL, email)
        applyPrefill(phoneInput, FIELD_PHONE, phone)
        updateSubmitButtonState()
    }

    private fun loadLatestProfilePrefill() {
        lifecycleScope.launch {
            when (val result = repository.getMyProfile()) {
                is NetworkResult.Success -> {
                    val profile = result.data
                    applyPrefill(fullNameInput, FIELD_FULL_NAME, profile.fullName.orEmpty())
                    applyPrefill(emailInput, FIELD_EMAIL, profile.email.orEmpty())
                    applyPrefill(phoneInput, FIELD_PHONE, profile.phoneNumber.orEmpty())
                }

                is NetworkResult.Error -> Unit
                NetworkResult.Loading -> Unit
            }
            updateSubmitButtonState()
        }
    }

    private fun applyPrefill(editText: EditText, fieldKey: String, value: String) {
        val candidate = value.trim()
        if (candidate.isBlank() || userEditedFields[fieldKey] == true) {
            return
        }

        if (editText.text?.toString().orEmpty() == candidate) {
            return
        }

        suppressEditTracking = true
        editText.setText(candidate)
        suppressEditTracking = false
    }

    private fun submitRegistration() {
        val fullName = fullNameInput.text.toString().trim()
        val email = emailInput.text.toString().trim()
        val phoneNumber = phoneInput.text.toString().trim()

        fullNameInput.error = null
        emailInput.error = null
        phoneInput.error = null

        var valid = true
        if (!Validators.isNonEmpty(fullName)) {
            fullNameInput.error = "Full name is required"
            valid = false
        }
        if (!Validators.isValidEmail(email)) {
            emailInput.error = "Enter a valid email address"
            valid = false
        }
        if (!Validators.isValidPhoneNumber(phoneNumber)) {
            phoneInput.error = "Phone number must start with 63 and be 12 digits long"
            valid = false
        }
        if (!termsCheckbox.isChecked) {
            valid = false
            Toast.makeText(this, "You must agree to the terms and conditions", Toast.LENGTH_SHORT).show()
        }

        if (!valid) {
            updateSubmitButtonState()
            return
        }

        presenter.submit(eventId, fullName, email, phoneNumber)
    }

    private fun updateSubmitButtonState() {
        val inputsValid = Validators.isNonEmpty(fullNameInput.text.toString()) &&
            Validators.isValidEmail(emailInput.text.toString()) &&
            Validators.isValidPhoneNumber(phoneInput.text.toString())
        submitButton.isEnabled = !isLoading && inputsValid && termsCheckbox.isChecked
        submitButton.alpha = if (submitButton.isEnabled) 1f else 0.6f
    }

    companion object {
        private const val FIELD_FULL_NAME = "fullName"
        private const val FIELD_EMAIL = "email"
        private const val FIELD_PHONE = "phone"

        private const val STATE_FULL_NAME_EDITED = "state_full_name_edited"
        private const val STATE_EMAIL_EDITED = "state_email_edited"
        private const val STATE_PHONE_EDITED = "state_phone_edited"
        private const val STATE_TERMS_CHECKED = "state_terms_checked"
    }
}
