package com.thedavelopers.eventqr.features.admin.users

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.PhoneInput
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.core.util.ScreenInsets
import com.thedavelopers.eventqr.core.util.Validators
import com.thedavelopers.eventqr.features.admin.AdminRepository
import com.thedavelopers.eventqr.ui.components.EventQrEmptyState
import com.thedavelopers.eventqr.ui.icons.FieldIcons
import com.thedavelopers.eventqr.ui.icons.RuleMarks
import com.thedavelopers.eventqr.ui.theme.applyEventQrSystemBarAppearance
import kotlinx.coroutines.launch

class CreateAdminAccountActivity : AppCompatActivity() {
    @VisibleForTesting
    internal lateinit var repository: AdminRepository
    private lateinit var sessionManager: SessionManager

    private lateinit var firstNameInput: EditText
    private lateinit var lastNameInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var phoneInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var confirmPasswordInput: EditText
    private lateinit var createButton: Button
    private lateinit var progressCreateAdmin: ProgressBar
    private lateinit var layoutCreateAdminError: View
    private lateinit var txtCreateAdminError: TextView
    private lateinit var requirementsLayout: View
    private lateinit var firstNameError: TextView
    private lateinit var lastNameError: TextView
    private lateinit var emailError: TextView
    private lateinit var phoneError: TextView
    private lateinit var passwordError: TextView
    private lateinit var confirmPasswordError: TextView
    private lateinit var passwordLengthRequirement: TextView
    private lateinit var passwordCapitalRequirement: TextView
    private lateinit var passwordLowercaseRequirement: TextView
    private lateinit var passwordSpecialRequirement: TextView
    private lateinit var passwordNumberRequirement: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_admin_account)
        ScreenInsets.padContentForNavigationBar(this)
        applyEventQrSystemBarAppearance()
        findViewById<TextView>(R.id.topBarTitle).setText(R.string.ui_admin_create_title)
        findViewById<View>(R.id.topBarBack).setOnClickListener { finish() }

        repository = AdminRepository(this)
        sessionManager = SessionManager(this)

        if (RoleMapper.normalizeRole(sessionManager.getUserRole()) != AccountRole.SUPER_ADMIN.name) {
            showPermissionDenied()
            return
        }

        bindViews()
        bindActions()
        listOf(emailInput, phoneInput, passwordInput, confirmPasswordInput)
            .forEach { FieldIcons.apply(it) }
        passwordInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updatePasswordRequirements(s?.toString().orEmpty())
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })
        updatePasswordRequirements(passwordInput.text.toString())
    }

    /** §6.8 row 18: permission denied is an in-flow state with one action, never a toast. */
    private fun showPermissionDenied() {
        listOf(R.id.formScroll, R.id.layoutCreateAdminFooter, R.id.dividerCreateAdminFooter)
            .forEach { findViewById<View>(it).visibility = View.GONE }
        findViewById<EventQrEmptyState>(R.id.layoutCreateAdminDenied).apply {
            visibility = View.VISIBLE
            setAction(getString(R.string.common_done), primary = false) { finish() }
        }
    }

    private fun bindViews() {
        firstNameInput = findViewById(R.id.inputAdminFirstName)
        lastNameInput = findViewById(R.id.inputAdminLastName)
        emailInput = findViewById(R.id.inputAdminEmail)
        phoneInput = findViewById(R.id.inputAdminPhone)
        passwordInput = findViewById(R.id.inputAdminPassword)
        confirmPasswordInput = findViewById(R.id.inputAdminConfirmPassword)
        createButton = findViewById(R.id.btnCreateAdminAccount)
        progressCreateAdmin = findViewById(R.id.progressCreateAdmin)
        layoutCreateAdminError = findViewById(R.id.layoutCreateAdminError)
        txtCreateAdminError = findViewById(R.id.txtCreateAdminError)
        requirementsLayout = findViewById(R.id.layoutPasswordRequirements)
        firstNameError = findViewById(R.id.txtAdminFirstNameError)
        lastNameError = findViewById(R.id.txtAdminLastNameError)
        emailError = findViewById(R.id.txtAdminEmailError)
        phoneError = findViewById(R.id.txtAdminPhoneError)
        passwordError = findViewById(R.id.txtAdminPasswordError)
        confirmPasswordError = findViewById(R.id.txtAdminConfirmPasswordError)
        passwordLengthRequirement = findViewById(R.id.txtPasswordLengthRequirement)
        passwordCapitalRequirement = findViewById(R.id.txtPasswordCapitalRequirement)
        passwordLowercaseRequirement = findViewById(R.id.txtPasswordLowercaseRequirement)
        passwordSpecialRequirement = findViewById(R.id.txtPasswordSpecialRequirement)
        passwordNumberRequirement = findViewById(R.id.txtPasswordNumberRequirement)
    }

    private fun bindActions() {
        createButton.setOnClickListener { submitCreateAdmin() }
        findViewById<View>(R.id.btnCreateAdminRetry).setOnClickListener {
            layoutCreateAdminError.visibility = View.GONE
            submitCreateAdmin()
        }
    }

    private fun submitCreateAdmin() {
        val firstName = firstNameInput.text.toString().trim()
        val lastName = lastNameInput.text.toString().trim()
        val email = emailInput.text.toString().trim()
        val phone = phoneInput.text.toString().trim()
        val password = passwordInput.text.toString()
        val confirmPassword = confirmPasswordInput.text.toString()

        var firstInvalid: EditText? = null

        fun mark(field: EditText, helper: TextView, messageRes: Int?) {
            setFieldError(field, helper, messageRes)
            if (messageRes != null && firstInvalid == null) firstInvalid = field
        }

        mark(firstNameInput, firstNameError, if (Validators.isNonEmpty(firstName)) null else R.string.create_admin_account_first_name_is_required)
        mark(lastNameInput, lastNameError, if (Validators.isNonEmpty(lastName)) null else R.string.create_admin_account_last_name_is_required)
        mark(emailInput, emailError, if (Validators.isValidEmail(email)) null else R.string.create_admin_account_enter_a_valid_email_address)

        // The hint shows "+63 912 345 6789", so spaces/dashes are dropped before the shared rule runs.
        val normalizedPhone = PhoneInput.toE164OrNull(phone)
        mark(phoneInput, phoneError, if (normalizedPhone == null) R.string.error_invalid_phone else null)

        val passwordTooLong = !Validators.passwordRequirements(password).withinMaxLength
        mark(
            passwordInput,
            passwordError,
            when {
                password.isEmpty() -> R.string.password_policy_hint
                passwordTooLong -> R.string.error_password_too_long
                !Validators.isValidSignUpPassword(password) -> R.string.password_policy_hint
                else -> null
            },
        )
        mark(
            confirmPasswordInput,
            confirmPasswordError,
            if (password == confirmPassword) null else R.string.password_error_mismatch,
        )

        if (firstInvalid != null) {
            firstInvalid?.requestFocus()
            return
        }

        val fullName = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ").trim()
        val phoneE164 = normalizedPhone ?: return

        layoutCreateAdminError.visibility = View.GONE
        setLoading(true)
        lifecycleScope.launch {
            when (val result = repository.createAdminAccount(fullName, email, phoneE164, password)) {
                is NetworkResult.Success -> {
                    setLoading(false)
                    finish()
                }
                is NetworkResult.Error -> {
                    setLoading(false)
                    val serverMessage = result.serverMessage?.takeIf { it.isNotBlank() } ?: result.message
                    if (isDuplicateEmail(serverMessage)) {
                        setFieldError(emailInput, emailError, R.string.ui_admin_create_email_taken)
                        emailInput.requestFocus()
                    } else {
                        txtCreateAdminError.text = serverMessage
                        layoutCreateAdminError.visibility = View.VISIBLE
                    }
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    /** The backend reports a taken address in prose, with no stable code in the current contract. */
    private fun isDuplicateEmail(message: String?): Boolean {
        val text = message?.lowercase().orEmpty()
        return text.contains("email") && DUPLICATE_EMAIL_HINTS.any { text.contains(it) }
    }

    /** Inline error under the field (§5.2): the helper turns into an 11sp error line, box takes 2dp error. */
    private fun setFieldError(field: EditText, helper: TextView, messageRes: Int?) {
        if (messageRes == null) {
            helper.text = null
            helper.visibility = View.GONE
        } else {
            helper.setText(messageRes)
            helper.visibility = View.VISIBLE
        }
        field.setBackgroundResource(
            if (messageRes == null) R.drawable.bg_ui_field else R.drawable.bg_ui_field_error,
        )
    }

    /**
     * §6.7: the primary keeps its width and label while a 20dp spinner replaces the visible text, so
     * the footer never jumps and TalkBack still announces the button.
     */
    private fun setLoading(isLoading: Boolean) {
        progressCreateAdmin.visibility = if (isLoading) View.VISIBLE else View.GONE
        createButton.isEnabled = !isLoading && Validators.isValidSignUpPassword(passwordInput.text.toString())
        createButton.setTextColor(
            ContextCompat.getColor(
                this,
                if (isLoading) android.R.color.transparent else R.color.ui_on_primary,
            ),
        )
    }

    private fun updatePasswordRequirements(password: String) {
        if (password.isEmpty()) {
            requirementsLayout.visibility = View.GONE
            createButton.isEnabled = false
            setFieldError(passwordInput, passwordError, null)
            return
        }
        requirementsLayout.visibility = View.VISIBLE

        val requirements = Validators.passwordRequirements(password)
        updateRequirement(passwordLengthRequirement, getString(R.string.password_req_length), requirements.hasMinLength)
        updateRequirement(passwordCapitalRequirement, getString(R.string.password_req_uppercase), requirements.hasCapital)
        updateRequirement(passwordLowercaseRequirement, getString(R.string.password_req_lowercase), requirements.hasLowercase)
        updateRequirement(passwordNumberRequirement, getString(R.string.password_req_number), requirements.hasNumber)
        updateRequirement(passwordSpecialRequirement, getString(R.string.password_req_special), requirements.hasSpecial)
        setFieldError(
            passwordInput,
            passwordError,
            if (requirements.withinMaxLength) null else R.string.error_password_too_long,
        )

        createButton.isEnabled = requirements.isValid
    }

    /** One checklist row (§6.1): the 16dp mark carries the state, never colour alone. */
    private fun updateRequirement(view: TextView, label: String, isMet: Boolean) {
        RuleMarks.apply(view, label, isMet)
    }

    private companion object {
        val DUPLICATE_EMAIL_HINTS = listOf("exist", "already", "taken", "registered")
    }
}
