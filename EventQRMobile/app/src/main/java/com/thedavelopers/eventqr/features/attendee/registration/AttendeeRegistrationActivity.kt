package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.core.util.UiStrings
import android.view.View
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.lifecycle.lifecycleScope
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.ui.theme.applyEventQrBottomInsetPadding
import com.thedavelopers.eventqr.features.terms.TermsLink
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
    private lateinit var fullNameRow: View
    private lateinit var fullNameDivider: View
    private lateinit var fullNameValue: TextView
    private lateinit var emailRow: View
    private lateinit var emailDivider: View
    private lateinit var emailValue: TextView
    private lateinit var phoneValue: TextView
    private lateinit var phoneRequired: TextView
    private lateinit var inlineError: TextView
    private lateinit var termsRow: View
    private lateinit var termsCheckbox: CheckBox
    private lateinit var submitButton: Button

    private var fullName: String = ""
    private var email: String = ""
    private var phone: String = ""
    private var isLoading = false
    private var profileEmail: String? = null
    // The inline gate explains the disabled primary, but only once the user has acted: on entry it
    // would read as a reprimand before anything was filled in or answered.
    private var hasInteracted = false

    /** Counts the profile refreshes so a return from the edit screen is testable. */
    @VisibleForTesting
    internal var profilePrefillRefreshes: Int = 0
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_registration)

        sessionManager = SessionManager(this)
        repository = AttendeeRepository(this)
        presenter = RegistrationPresenter(
            this,
            repository,
            UiStrings(this),
            ownEmailProvider = { profileEmail?.takeIf { it.isNotBlank() } ?: sessionManager.getEmail() },
            ownEmailMessage = getString(R.string.registration_own_email_required),
        )
        eventId = intent.getStringExtra(EXTRA_EVENT_ID).orEmpty()

        findViewById<ImageButton>(R.id.nav_header_back).setOnClickListener { finish() }
        findViewById<View>(R.id.layoutBottomBar).applyEventQrBottomInsetPadding()

        eventCategoryText = findViewById(R.id.txtEventCategory)
        eventTitleText = findViewById(R.id.txtEventTitle)
        eventDateTimeVenueText = findViewById(R.id.txtEventDateTimeVenue)
        fullNameRow = findViewById(R.id.rowRegistrationFullName)
        fullNameDivider = findViewById(R.id.dividerRegistrationFullName)
        fullNameValue = findViewById(R.id.txtRegistrationFullName)
        emailRow = findViewById(R.id.rowRegistrationEmail)
        emailDivider = findViewById(R.id.dividerRegistrationEmail)
        emailValue = findViewById(R.id.txtRegistrationEmail)
        phoneValue = findViewById(R.id.txtRegistrationPhone)
        phoneRequired = findViewById(R.id.txtRegistrationPhoneRequired)
        inlineError = findViewById(R.id.txtRegistrationInlineError)
        termsRow = findViewById(R.id.rowRegistrationTerms)
        termsCheckbox = findViewById(R.id.chkRegistrationTerms)
        submitButton = findViewById(R.id.btnSubmitRegistration)

        restoreState(savedInstanceState)
        bindEventSummary()
        applyInitialPrefill()

        findViewById<View>(R.id.btnEditDetailsInProfile).setOnClickListener {
            hasInteracted = true
            updateSubmitButtonState()
            startActivity(Intent(this, AttendeeEditProfileActivity::class.java))
        }
        termsRow.setOnClickListener { termsCheckbox.toggle() }
        // WCAG 4.1.2 / 3.3.2: the row is one checkable control. Without the role and the live
        // checked state, TalkBack read the sentence with no answer to "checked or not"; the mark
        // itself stays out of the tree so the row is the one stop that toggles it.
        ViewCompat.setAccessibilityDelegate(termsRow, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = CheckBox::class.java.name
                info.isCheckable = true
                info.isChecked = termsCheckbox.isChecked
            }
        })
        termsCheckbox.setOnCheckedChangeListener { _, _ ->
            hasInteracted = true
            updateSubmitButtonState()
        }
        TermsLink.apply(findViewById(R.id.txtRegistrationTerms), getString(R.string.terms_link_event))
        submitButton.setOnClickListener {
            hasInteracted = true
            submitRegistration()
        }

        updateSubmitButtonState()
    }

    override fun onResume() {
        super.onResume()
        // Also runs on the return from "Edit in profile": a phone added there has to reach the
        // phone row and unblock Submit, which gates on the phone value.
        loadLatestProfilePrefill()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_TERMS_CHECKED, termsCheckbox.isChecked)
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }

    override fun showLoading(isLoading: Boolean) {
        this.isLoading = isLoading
        submitButton.text = getString(
            if (isLoading) R.string.ui_attendee_submitting else R.string.ui_attendee_confirm_registration,
        )
        updateSubmitButtonState()
    }

    override fun showMessage(message: String) {
        // Server-side failures are actionable where the form is; keep them inline, not a toast.
        showInlineError(message)
    }

    override fun showFieldError(field: String, message: String?) {
        if (message.isNullOrBlank()) return
        showInlineError(
            if (field == FIELD_PHONE && message == Validators.PHONE_ERROR) {
                getString(R.string.error_invalid_phone)
            } else {
                message
            },
        )
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

        termsCheckbox.isChecked = savedInstanceState.getBoolean(STATE_TERMS_CHECKED, false)
    }

    private fun bindEventSummary() {
        val category = intent.getStringExtra(EXTRA_EVENT_CATEGORY).orEmpty().ifBlank { "Event" }
        val title = intent.getStringExtra(EXTRA_EVENT_TITLE).orEmpty().ifBlank { getString(R.string.event_registration_register_for_event) }
        val date = intent.getStringExtra(EXTRA_EVENT_START).orEmpty().ifBlank { getString(R.string.ui_attendee_date_tbd) }
        val venue = intent.getStringExtra(EXTRA_EVENT_LOCATION).orEmpty().ifBlank { getString(R.string.ui_attendee_venue_tbd) }

        eventCategoryText.text = category
        eventTitleText.text = title
        eventDateTimeVenueText.text = getString(R.string.ui_attendee_event_meta, date, venue)
    }

    // EventQR - registrant info display-only, pulled from account profile (edit profile handled separately, not here)
    private fun applyInitialPrefill() {
        val fullName = intent.getStringExtra(EXTRA_PREFILL_FULL_NAME).orEmpty().ifBlank { sessionManager.getFullName().orEmpty() }
        val email = intent.getStringExtra(EXTRA_PREFILL_EMAIL).orEmpty().ifBlank { sessionManager.getEmail().orEmpty() }
        val phone = intent.getStringExtra(EXTRA_PREFILL_PHONE).orEmpty().ifBlank { sessionManager.getPhone().orEmpty() }

        applyPrefill(FIELD_FULL_NAME, fullName)
        applyPrefill(FIELD_EMAIL, email)
        applyPrefill(FIELD_PHONE, phone)
        renderDetails()
        updateSubmitButtonState()
    }

    private fun loadLatestProfilePrefill() {
        profilePrefillRefreshes++
        lifecycleScope.launch {
            when (val result = repository.getMyProfile()) {
                is NetworkResult.Success -> {
                    val profile = result.data
                    profileEmail = profile.email
                    applyProfilePrefill(profile.fullName.orEmpty(), profile.email.orEmpty(), profile.phoneNumber.orEmpty())
                }

                is NetworkResult.Error -> Unit
                NetworkResult.Loading -> Unit
            }
            updateSubmitButtonState()
        }
    }

    /** Applies a profile snapshot, including the re-render the prefill path needs on return. */
    internal fun applyProfilePrefill(fullNameValue: String, emailValue: String, phoneValue: String) {
        applyPrefill(FIELD_FULL_NAME, fullNameValue)
        applyPrefill(FIELD_EMAIL, emailValue)
        applyPrefill(FIELD_PHONE, phoneValue)
        renderDetails()
        updateSubmitButtonState()
    }

    private fun applyPrefill(fieldKey: String, value: String) {
        val candidate = value.trim()
        if (candidate.isBlank()) return

        when (fieldKey) {
            FIELD_FULL_NAME -> fullName = candidate
            FIELD_EMAIL -> email = candidate
            FIELD_PHONE -> phone = candidate
        }
    }

    /**
     * Rows with no value hide with their hairline; PHONE always renders, printing `Not set` because
     * the fix is one tap away on the `Edit in profile` link (§6.2).
     */
    private fun renderDetails() {
        val hasName = fullName.isNotBlank()
        fullNameRow.visibility = if (hasName) View.VISIBLE else View.GONE
        fullNameDivider.visibility = fullNameRow.visibility
        fullNameValue.text = fullName

        val hasEmail = email.isNotBlank()
        emailRow.visibility = if (hasEmail) View.VISIBLE else View.GONE
        emailDivider.visibility = emailRow.visibility
        emailValue.text = email

        phoneValue.text = phone.ifBlank { getString(R.string.ui_attendee_not_set) }
    }

    private fun submitRegistration() {
        hasInteracted = true
        val problem = when {
            !Validators.isNonEmpty(fullName) -> getString(R.string.attendee_registration_full_name_is_required)
            !Validators.isValidEmail(email) -> getString(R.string.create_admin_account_enter_a_valid_email_address)
            !Validators.isValidPhoneNumber(phone) -> getString(R.string.error_invalid_phone)
            !termsCheckbox.isChecked -> getString(R.string.attendee_registration_you_must_agree_to_the_terms_and_cond)
            else -> null
        }

        if (problem != null) {
            showMessage(problem)
            updateSubmitButtonState()
            return
        }

        presenter.submit(eventId, fullName, email, phone)
    }

    private fun updateSubmitButtonState() {
        val inputsValid = Validators.isNonEmpty(fullName) &&
            Validators.isValidEmail(email) &&
            Validators.isValidPhoneNumber(phone)
        submitButton.isEnabled = !isLoading && inputsValid && termsCheckbox.isChecked
        // Same silence rule as the inline gate: no red marker before the user has acted.
        phoneRequired.visibility =
            if (!hasInteracted || Validators.isValidPhoneNumber(phone)) View.GONE else View.VISIBLE

        // The primary is disabled with no explanation otherwise: name the first thing the gate is
        // waiting for, next to the control that is blocked. Before the first interaction the gate
        // stays silent, so the form does not open with an error.
        if (isLoading || !hasInteracted) {
            showInlineError(null)
        } else {
            showInlineError(blockingMessage())
        }
    }

    /** The first unmet requirement, in the same order as [submitRegistration]. */
    private fun blockingMessage(): String? = when {
        !Validators.isNonEmpty(fullName) -> getString(R.string.ui_attendee_add_name_to_continue)
        !Validators.isValidEmail(email) -> getString(R.string.ui_attendee_add_email_to_continue)
        !Validators.isValidPhoneNumber(phone) -> getString(R.string.ui_attendee_add_phone_to_continue)
        !termsCheckbox.isChecked -> getString(R.string.ui_attendee_agree_terms_to_continue)
        else -> null
    }

    private fun showInlineError(message: String?) {
        inlineError.text = message.orEmpty()
        inlineError.visibility = if (message.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    companion object {
        private const val FIELD_FULL_NAME = "fullName"
        private const val FIELD_EMAIL = "email"
        private const val FIELD_PHONE = "phone"

        private const val STATE_TERMS_CHECKED = "state_terms_checked"
    }
}
