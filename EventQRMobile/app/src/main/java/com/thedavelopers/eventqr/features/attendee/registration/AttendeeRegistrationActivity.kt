package com.thedavelopers.eventqr.features.attendee

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.util.Validators

open class AttendeeRegistrationActivity : AppCompatActivity(), RegistrationContract.View {
    private lateinit var presenter: RegistrationPresenter
    private lateinit var eventId: String
    private lateinit var firstNameInput: EditText
    private lateinit var lastNameInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var phoneInput: EditText
    private lateinit var studentIdInput: EditText
    private lateinit var dietaryInput: EditText
    private lateinit var emergencyInput: EditText
    private lateinit var submitButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_registration)

        presenter = RegistrationPresenter(this, AttendeeRepository(this))
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

        val fullPrefillName = intent.getStringExtra(EXTRA_PREFILL_FULL_NAME).orEmpty()
        if (fullPrefillName.contains(" ")) {
            val parts = fullPrefillName.split(" ", limit = 2)
            firstNameInput.setText(parts[0])
            lastNameInput.setText(parts[1])
        } else {
            firstNameInput.setText(fullPrefillName)
        }

        emailInput.setText(intent.getStringExtra(EXTRA_PREFILL_EMAIL).orEmpty())

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
}
