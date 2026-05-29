package com.thedavelopers.eventqr.features.attendee

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
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
    private data class PrefillValue(val value: String, val source: String)

    private lateinit var presenter: RegistrationPresenter
    private lateinit var repository: AttendeeRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var eventId: String
    private lateinit var firstNameInput: EditText
    private lateinit var lastNameInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var phoneInput: EditText
    private lateinit var studentIdInput: EditText
    private lateinit var dietaryInput: EditText
    private lateinit var emergencyInput: EditText
    private lateinit var submitButton: Button
    private lateinit var autofillStatusLayout: LinearLayout
    private lateinit var autofillStatusText: TextView

    private val userEditedFields = mutableMapOf(
        FIELD_FIRST_NAME to false,
        FIELD_LAST_NAME to false,
        FIELD_EMAIL to false,
        FIELD_PHONE to false,
        FIELD_STUDENT_ID to false,
        FIELD_EMERGENCY_CONTACT to false,
    )
    private val lastAppliedSources = mutableMapOf<String, String>()
    private var suppressEditTracking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_registration)

        sessionManager = SessionManager(this)
        repository = AttendeeRepository(this)
        presenter = RegistrationPresenter(this, repository)
        eventId = intent.getStringExtra(EXTRA_EVENT_ID).orEmpty()

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnCancel).setOnClickListener { finish() }

        firstNameInput = findViewById(R.id.edtRegistrationFirstName)
        lastNameInput = findViewById(R.id.edtRegistrationLastName)
        emailInput = findViewById(R.id.edtRegistrationEmail)
        phoneInput = findViewById(R.id.edtRegistrationPhone)
        studentIdInput = findViewById(R.id.edtRegistrationStudentId)
        emergencyInput = findViewById(R.id.edtEmergencyContact)
        submitButton = findViewById(R.id.btnSubmitRegistration)
        autofillStatusLayout = findViewById(R.id.layoutAutofillStatus)
        autofillStatusText = findViewById(R.id.txtAutofillStatus)

        restoreEditedFieldState(savedInstanceState)
        attachUserEditTracking()

        applyInitialPrefill()
        loadLatestProfilePrefill()

        submitButton.setOnClickListener {
            val firstName = firstNameInput.text.toString().trim()
            val lastName = lastNameInput.text.toString().trim()
            val phoneNumber = phoneInput.text.toString().trim()

            firstNameInput.error = null
            lastNameInput.error = null
            emailInput.error = null
            phoneInput.error = null

            var valid = true
            if (!Validators.isNonEmpty(firstName)) {
                firstNameInput.error = "First name is required"
                valid = false
            }
            if (!Validators.isNonEmpty(lastName)) {
                lastNameInput.error = "Last name is required"
                valid = false
            }
            if (!Validators.isValidPhoneNumber(phoneNumber)) {
                phoneInput.error = "Phone number must start with 63 and be 12 digits long"
                valid = false
            }
            if (!valid) {
                return@setOnClickListener
            }

            val fullName = "${firstNameInput.text} ${lastNameInput.text}".trim()
            presenter.submit(
                eventId,
                fullName,
                emailInput.text.toString(),
                phoneNumber,
            )
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_FIRST_NAME_EDITED, userEditedFields[FIELD_FIRST_NAME] == true)
        outState.putBoolean(STATE_LAST_NAME_EDITED, userEditedFields[FIELD_LAST_NAME] == true)
        outState.putBoolean(STATE_EMAIL_EDITED, userEditedFields[FIELD_EMAIL] == true)
        outState.putBoolean(STATE_PHONE_EDITED, userEditedFields[FIELD_PHONE] == true)
        outState.putBoolean(STATE_STUDENT_ID_EDITED, userEditedFields[FIELD_STUDENT_ID] == true)
        outState.putBoolean(STATE_EMERGENCY_EDITED, userEditedFields[FIELD_EMERGENCY_CONTACT] == true)
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }

    override fun showLoading(isLoading: Boolean) {
        submitButton.isEnabled = !isLoading
        submitButton.text = if (isLoading) "Submitting..." else "Register"
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showFieldError(field: String, message: String?) {
        when (field) {
            "fullName" -> {
                firstNameInput.error = message
                lastNameInput.error = message
            }
            "email" -> emailInput.error = message
            "phone" -> phoneInput.error = message
        }
    }

    override fun openQr(registrationId: String, qrCredentialId: String) {
        startActivity(
            android.content.Intent(this, AttendeeQrCredentialActivity::class.java)
                .putExtra(EXTRA_REGISTRATION_ID, registrationId)
                .putExtra(EXTRA_QR_CREDENTIAL_ID, qrCredentialId)
        )
        finish()
    }

    private fun restoreEditedFieldState(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            return
        }

        userEditedFields[FIELD_FIRST_NAME] = savedInstanceState.getBoolean(STATE_FIRST_NAME_EDITED, false)
        userEditedFields[FIELD_LAST_NAME] = savedInstanceState.getBoolean(STATE_LAST_NAME_EDITED, false)
        userEditedFields[FIELD_EMAIL] = savedInstanceState.getBoolean(STATE_EMAIL_EDITED, false)
        userEditedFields[FIELD_PHONE] = savedInstanceState.getBoolean(STATE_PHONE_EDITED, false)
        userEditedFields[FIELD_STUDENT_ID] = savedInstanceState.getBoolean(STATE_STUDENT_ID_EDITED, false)
        userEditedFields[FIELD_EMERGENCY_CONTACT] = savedInstanceState.getBoolean(STATE_EMERGENCY_EDITED, false)
    }

    private fun attachUserEditTracking() {
        trackUserEdits(firstNameInput, FIELD_FIRST_NAME)
        trackUserEdits(lastNameInput, FIELD_LAST_NAME)
        trackUserEdits(emailInput, FIELD_EMAIL)
        trackUserEdits(phoneInput, FIELD_PHONE)
        trackUserEdits(studentIdInput, FIELD_STUDENT_ID)
        trackUserEdits(emergencyInput, FIELD_EMERGENCY_CONTACT)
    }

    private fun trackUserEdits(editText: EditText, fieldKey: String) {
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!suppressEditTracking) {
                    userEditedFields[fieldKey] = true
                }
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun applyInitialPrefill() {
        val fullName = intent.getStringExtra(EXTRA_PREFILL_FULL_NAME).orEmpty().ifBlank { sessionManager.getFullName().orEmpty() }
        val email = intent.getStringExtra(EXTRA_PREFILL_EMAIL).orEmpty().ifBlank { sessionManager.getEmail().orEmpty() }
        val phone = intent.getStringExtra(EXTRA_PREFILL_PHONE).orEmpty().ifBlank { sessionManager.getPhone().orEmpty() }
        val studentId = intent.getStringExtra(EXTRA_PREFILL_STUDENT_ID).orEmpty()
        val emergencyContact = intent.getStringExtra(EXTRA_PREFILL_EMERGENCY_CONTACT).orEmpty()

        val filledFields = mutableListOf<String>()
        if (applyFullNamePrefill(fullName, SOURCE_INTENT_SESSION)) filledFields += "fullName"
        if (applyPrefill(emailInput, FIELD_EMAIL, email, SOURCE_INTENT_SESSION)) filledFields += "email"
        if (applyPrefill(phoneInput, FIELD_PHONE, phone, SOURCE_INTENT_SESSION)) filledFields += "phone"
        if (applyPrefill(studentIdInput, FIELD_STUDENT_ID, studentId, SOURCE_INTENT_SESSION)) filledFields += "studentId"
        if (applyPrefill(emergencyInput, FIELD_EMERGENCY_CONTACT, emergencyContact, SOURCE_INTENT_SESSION)) filledFields += "emergencyContact"

        logAutofillSnapshot(
            source = SOURCE_INTENT_SESSION,
            fieldsFilled = filledFields,
            profileLoadSucceeded = null,
            preservedFields = emptyList(),
        )
    }

    private fun loadLatestProfilePrefill() {
        showAutofillLoading(true)
        lifecycleScope.launch {
            val preservedFields = userEditedFields.filterValues { it }.keys.toList()
            when (val result = repository.getMyProfile()) {
                is NetworkResult.Success -> {
                    val profile = result.data
                    val fieldsFilled = mutableListOf<String>()
                    if (applyFullNamePrefill(profile.fullName.orEmpty(), SOURCE_PROFILE)) fieldsFilled += "fullName"
                    if (applyPrefill(emailInput, FIELD_EMAIL, profile.email.orEmpty(), SOURCE_PROFILE)) fieldsFilled += "email"
                    if (applyPrefill(phoneInput, FIELD_PHONE, profile.phoneNumber.orEmpty(), SOURCE_PROFILE)) fieldsFilled += "phone"

                    logAutofillSnapshot(
                        source = SOURCE_PROFILE,
                        fieldsFilled = fieldsFilled,
                        profileLoadSucceeded = true,
                        preservedFields = preservedFields,
                    )
                }

                is NetworkResult.Error -> {
                    logAutofillSnapshot(
                        source = SOURCE_PROFILE,
                        fieldsFilled = emptyList(),
                        profileLoadSucceeded = false,
                        preservedFields = preservedFields,
                    )
                }

                NetworkResult.Loading -> Unit
            }
            showAutofillLoading(false)
        }
    }

    private fun applyFullNamePrefill(fullName: String, source: String): Boolean {
        if (fullName.isBlank()) {
            return false
        }

        val parts = splitFullName(fullName)
        val filledFirst = applyPrefill(firstNameInput, FIELD_FIRST_NAME, parts.first, source)
        val filledLast = applyPrefill(lastNameInput, FIELD_LAST_NAME, parts.second, source)
        return filledFirst || filledLast
    }

    private fun splitFullName(fullName: String): Pair<String, String> {
        val trimmed = fullName.trim()
        if (trimmed.isBlank()) {
            return "" to ""
        }

        val parts = trimmed.split(Regex("\\s+"), limit = 2)
        return if (parts.size == 1) {
            parts[0] to ""
        } else {
            parts[0] to parts[1]
        }
    }

    private fun applyPrefill(editText: EditText, fieldKey: String, value: String, source: String): Boolean {
        val candidate = value.trim()
        if (candidate.isBlank() || userEditedFields[fieldKey] == true) {
            return false
        }

        val current = editText.text?.toString().orEmpty()
        if (current == candidate) {
            return false
        }

        suppressEditTracking = true
        editText.setText(candidate)
        suppressEditTracking = false
        lastAppliedSources[fieldKey] = source
        return true
    }

    private fun showAutofillLoading(isLoading: Boolean) {
        autofillStatusLayout.visibility = if (isLoading) android.view.View.VISIBLE else android.view.View.GONE
        autofillStatusText.text = if (isLoading) "Loading saved profile..." else ""
    }

    private fun logAutofillSnapshot(
        source: String,
        fieldsFilled: List<String>,
        profileLoadSucceeded: Boolean?,
        preservedFields: List<String>,
    ) {
        Log.d(
            TAG_AUTOFILL,
            "eventId=$eventId,source=$source,fieldsFilled=${fieldsFilled.joinToString("|")}," +
                "profileLoad=${profileLoadSucceeded?.let { if (it) "success" else "failure" } ?: "n/a"}," +
                "preservedEditedFields=${preservedFields.joinToString("|")}," +
                "userEditedFlags=${userEditedFields.filterValues { it }.keys.joinToString("|")}," +
                "lastAppliedSources=${lastAppliedSources.entries.joinToString("|") { "${it.key}:${it.value}" }}"
        )
    }

    companion object {
        private const val TAG_AUTOFILL = "RegistrationAutofill"
        private const val SOURCE_INTENT_SESSION = "intent/session"
        private const val SOURCE_PROFILE = "profile"

        private const val FIELD_FIRST_NAME = "firstName"
        private const val FIELD_LAST_NAME = "lastName"
        private const val FIELD_EMAIL = "email"
        private const val FIELD_PHONE = "phone"
        private const val FIELD_STUDENT_ID = "studentId"
        private const val FIELD_EMERGENCY_CONTACT = "emergencyContact"

        private const val STATE_FIRST_NAME_EDITED = "state_first_name_edited"
        private const val STATE_LAST_NAME_EDITED = "state_last_name_edited"
        private const val STATE_EMAIL_EDITED = "state_email_edited"
        private const val STATE_PHONE_EDITED = "state_phone_edited"
        private const val STATE_STUDENT_ID_EDITED = "state_student_id_edited"
        private const val STATE_EMERGENCY_EDITED = "state_emergency_edited"
    }
}
