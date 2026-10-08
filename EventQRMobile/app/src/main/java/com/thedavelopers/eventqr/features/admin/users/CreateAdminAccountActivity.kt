package com.thedavelopers.eventqr.features.admin.users

import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.core.util.Validators
import com.thedavelopers.eventqr.features.admin.AdminRepository
import kotlinx.coroutines.launch

class CreateAdminAccountActivity : AppCompatActivity() {
    private lateinit var repository: AdminRepository
    private lateinit var sessionManager: SessionManager

    private lateinit var firstNameInput: EditText
    private lateinit var lastNameInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var phoneInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var confirmPasswordInput: EditText
    private lateinit var createButton: Button
    private lateinit var requirementsLayout: View
    private lateinit var passwordLengthRequirement: TextView
    private lateinit var passwordCapitalRequirement: TextView
    private lateinit var passwordLowercaseRequirement: TextView
    private lateinit var passwordSpecialRequirement: TextView
    private lateinit var passwordNumberRequirement: TextView
    private lateinit var passwordStrengthText: TextView
    private lateinit var strengthBars: List<View>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_admin_account)

        repository = AdminRepository(this)
        sessionManager = SessionManager(this)

        if (RoleMapper.normalizeRole(sessionManager.getUserRole()) != AccountRole.SUPER_ADMIN.name) {
            Toast.makeText(this, this.getString(R.string.create_admin_account_only_super_admin_can_create_admin_ac), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        bindViews()
        bindActions()
        configurePasswordToggle(passwordInput)
        configurePasswordToggle(confirmPasswordInput)
        passwordInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updatePasswordRequirements(s?.toString().orEmpty())
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })
        updatePasswordRequirements(passwordInput.text.toString())
    }

    private fun bindViews() {
        firstNameInput = findViewById(R.id.inputAdminFirstName)
        lastNameInput = findViewById(R.id.inputAdminLastName)
        emailInput = findViewById(R.id.inputAdminEmail)
        phoneInput = findViewById(R.id.inputAdminPhone)
        passwordInput = findViewById(R.id.inputAdminPassword)
        confirmPasswordInput = findViewById(R.id.inputAdminConfirmPassword)
        createButton = findViewById(R.id.btnCreateAdminAccount)
        requirementsLayout = findViewById(R.id.layoutPasswordRequirements)
        passwordLengthRequirement = findViewById(R.id.txtPasswordLengthRequirement)
        passwordCapitalRequirement = findViewById(R.id.txtPasswordCapitalRequirement)
        passwordLowercaseRequirement = findViewById(R.id.txtPasswordLowercaseRequirement)
        passwordSpecialRequirement = findViewById(R.id.txtPasswordSpecialRequirement)
        passwordNumberRequirement = findViewById(R.id.txtPasswordNumberRequirement)
        passwordStrengthText = findViewById(R.id.txtPasswordStrength)
        strengthBars = listOf(
            findViewById(R.id.viewStrength1),
            findViewById(R.id.viewStrength2),
            findViewById(R.id.viewStrength3),
            findViewById(R.id.viewStrength4),
        )
    }

    private fun bindActions() {
        findViewById<View>(R.id.nav_header_back).setOnClickListener { finish() }
        createButton.setOnClickListener { submitCreateAdmin() }
    }

    private fun submitCreateAdmin() {
        val firstName = firstNameInput.text.toString().trim()
        val lastName = lastNameInput.text.toString().trim()
        val email = emailInput.text.toString().trim()
        val phone = phoneInput.text.toString().trim()
        val password = passwordInput.text.toString()
        val confirmPassword = confirmPasswordInput.text.toString()

        var valid = true
        if (!Validators.isNonEmpty(firstName)) {
            firstNameInput.error = getString(R.string.create_admin_account_first_name_is_required)
            valid = false
        } else {
            firstNameInput.error = null
        }

        if (!Validators.isNonEmpty(lastName)) {
            lastNameInput.error = getString(R.string.create_admin_account_last_name_is_required)
            valid = false
        } else {
            lastNameInput.error = null
        }

        if (!Validators.isValidEmail(email)) {
            emailInput.error = getString(R.string.create_admin_account_enter_a_valid_email_address)
            valid = false
        } else {
            emailInput.error = null
        }

        if (!Validators.isValidPhoneNumber(phone)) {
            phoneInput.error = getString(R.string.error_invalid_phone)
            valid = false
        } else {
            phoneInput.error = null
        }

        if (!Validators.isValidSignUpPassword(password)) {
            passwordInput.error = Validators.passwordRequirements(password).let {
                if (it.isOtherwiseValid && !it.withinMaxLength) getString(R.string.error_password_too_long)
                else getString(R.string.password_policy_hint)
            }
            valid = false
        } else {
            passwordInput.error = null
        }

        if (password != confirmPassword) {
            confirmPasswordInput.error = getString(R.string.password_error_mismatch)
            valid = false
        } else {
            confirmPasswordInput.error = null
        }

        if (!valid) return

        val fullName = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ").trim()

        setLoading(true)
        lifecycleScope.launch {
            when (val result = repository.createAdminAccount(fullName, email, phone, password)) {
                is NetworkResult.Success -> {
                    setLoading(false)
                    Toast.makeText(
                        this@CreateAdminAccountActivity,
                        result.message ?: "Admin account created",
                        Toast.LENGTH_SHORT,
                    ).show()
                    finish()
                }
                is NetworkResult.Error -> {
                    setLoading(false)
                    Toast.makeText(this@CreateAdminAccountActivity, result.message, Toast.LENGTH_SHORT).show()
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        createButton.isEnabled = !isLoading && Validators.isValidSignUpPassword(passwordInput.text.toString())
        createButton.text = getString(if (isLoading) R.string.create_admin_account_creating_admin else R.string.common_create_admin_account)
    }

    private fun updatePasswordRequirements(password: String) {
        if (password.isEmpty()) {
            requirementsLayout.visibility = View.GONE
            createButton.isEnabled = false
            return
        }
        requirementsLayout.visibility = View.VISIBLE

        val requirements = Validators.passwordRequirements(password)
        updateRequirement(passwordLengthRequirement, getString(R.string.password_req_length), requirements.hasMinLength)
        updateRequirement(passwordCapitalRequirement, getString(R.string.password_req_uppercase), requirements.hasCapital)
        updateRequirement(passwordLowercaseRequirement, getString(R.string.password_req_lowercase), requirements.hasLowercase)
        updateRequirement(passwordNumberRequirement, getString(R.string.password_req_number), requirements.hasNumber)
        updateRequirement(passwordSpecialRequirement, getString(R.string.password_req_special), requirements.hasSpecial)
        passwordInput.error = if (!requirements.withinMaxLength) getString(R.string.error_password_too_long) else null

        updateStrengthUI(requirements.strengthLevel)
        createButton.isEnabled = requirements.isValid
    }

    private fun updateRequirement(view: TextView, label: String, isMet: Boolean) {
        view.text = "${if (isMet) "✓" else "○"} $label"
        view.setTextColor(getColor(if (isMet) R.color.eventqr_success else R.color.eventqr_muted))
    }

    private fun updateStrengthUI(metCount: Int) {
        val (colorRes, label) = when (metCount) {
            0 -> R.color.eventqr_muted to ""
            1 -> R.color.eventqr_error to getString(R.string.password_strength_weak)
            2 -> R.color.eventqr_warning to getString(R.string.password_strength_fair)
            3 -> R.color.eventqr_info to getString(R.string.password_strength_good)
            4 -> R.color.eventqr_success to getString(R.string.password_strength_strong)
            else -> R.color.eventqr_muted to ""
        }

        passwordStrengthText.text = label
        passwordStrengthText.setTextColor(if (metCount > 0) getColor(colorRes) else getColor(R.color.eventqr_muted))
        strengthBars.forEachIndexed { index, view ->
            view.background.mutate().setTint(
                if (index < metCount) getColor(colorRes) else getColor(R.color.eventqr_border),
            )
        }
    }

    private fun configurePasswordToggle(input: EditText) {
        input.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_UP && event.rawX >= input.right - input.compoundPaddingEnd) {
                val isVisible = input.inputType == (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
                if (isVisible) {
                    input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    input.setCompoundDrawablesWithIntrinsicBounds(
                        input.compoundDrawables[0],
                        null,
                        ContextCompat.getDrawable(this, R.drawable.ic_visibility_on),
                        null,
                    )
                } else {
                    input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    input.setCompoundDrawablesWithIntrinsicBounds(
                        input.compoundDrawables[0],
                        null,
                        ContextCompat.getDrawable(this, R.drawable.ic_visibility_off),
                        null,
                    )
                }
                input.setSelection(input.text.length)
                view.performClick()
                true
            } else {
                false
            }
        }
    }
}
