package com.thedavelopers.eventqr.features.admin.users

import android.content.Intent
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
import com.thedavelopers.eventqr.features.admin.dashboard.SuperAdminDashboardActivity
import kotlinx.coroutines.launch

class CreateAdminAccountActivity : AppCompatActivity() {
    private lateinit var repository: AdminRepository
    private lateinit var sessionManager: SessionManager

    private lateinit var fullNameInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var phoneInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var confirmPasswordInput: EditText
    private lateinit var createButton: Button
    private lateinit var requirementsLayout: View
    private lateinit var passwordLengthRequirement: TextView
    private lateinit var passwordCapitalRequirement: TextView
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
            Toast.makeText(this, "Only Super Admin can create admin accounts.", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, SuperAdminDashboardActivity::class.java))
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
        fullNameInput = findViewById(R.id.inputAdminFullName)
        emailInput = findViewById(R.id.inputAdminEmail)
        phoneInput = findViewById(R.id.inputAdminPhone)
        passwordInput = findViewById(R.id.inputAdminPassword)
        confirmPasswordInput = findViewById(R.id.inputAdminConfirmPassword)
        createButton = findViewById(R.id.btnCreateAdminAccount)
        requirementsLayout = findViewById(R.id.layoutPasswordRequirements)
        passwordLengthRequirement = findViewById(R.id.txtPasswordLengthRequirement)
        passwordCapitalRequirement = findViewById(R.id.txtPasswordCapitalRequirement)
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
        findViewById<View>(R.id.buttonBack).setOnClickListener { finish() }
        createButton.setOnClickListener { submitCreateAdmin() }
    }

    private fun submitCreateAdmin() {
        val fullName = fullNameInput.text.toString().trim()
        val email = emailInput.text.toString().trim()
        val phone = phoneInput.text.toString().trim()
        val phoneValue = phone.ifBlank { null }
        val password = passwordInput.text.toString().trim()
        val confirmPassword = confirmPasswordInput.text.toString().trim()

        var valid = true
        if (!Validators.isNonEmpty(fullName)) {
            fullNameInput.error = "Full name is required"
            valid = false
        } else {
            fullNameInput.error = null
        }

        if (!Validators.isValidEmail(email)) {
            emailInput.error = "Enter a valid email address"
            valid = false
        } else {
            emailInput.error = null
        }

        if (phoneValue != null && !Validators.isValidPhoneNumber(phoneValue)) {
            phoneInput.error = "Phone number must start with 63 and be 12 digits long"
            valid = false
        } else {
            phoneInput.error = null
        }

        if (!Validators.isValidSignUpPassword(password)) {
            passwordInput.error = "Password must meet all requirements"
            valid = false
        } else {
            passwordInput.error = null
        }

        if (password != confirmPassword) {
            confirmPasswordInput.error = "Passwords do not match"
            valid = false
        } else {
            confirmPasswordInput.error = null
        }

        if (!valid) return

        setLoading(true)
        lifecycleScope.launch {
            when (val result = repository.createAdminAccount(fullName, email, phoneValue, password)) {
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
        createButton.text = if (isLoading) "Creating admin..." else "Create Admin Account"
    }

    private fun updatePasswordRequirements(password: String) {
        if (password.isEmpty()) {
            requirementsLayout.visibility = View.GONE
            createButton.isEnabled = false
            return
        }
        requirementsLayout.visibility = View.VISIBLE

        val requirements = Validators.passwordRequirements(password)
        updateRequirement(passwordLengthRequirement, "At least 8 characters", requirements.hasMinLength)
        updateRequirement(passwordCapitalRequirement, "One uppercase letter", requirements.hasCapital)
        updateRequirement(passwordNumberRequirement, "One number", requirements.hasNumber)
        updateRequirement(passwordSpecialRequirement, "One special character", requirements.hasSpecial)

        val metCount = listOf(
            requirements.hasMinLength,
            requirements.hasCapital,
            requirements.hasNumber,
            requirements.hasSpecial,
        ).count { it }

        updateStrengthUI(metCount)
        createButton.isEnabled = requirements.isValid
    }

    private fun updateRequirement(view: TextView, label: String, isMet: Boolean) {
        view.text = "${if (isMet) "OK" else "--"} $label"
        view.setTextColor(getColor(if (isMet) R.color.eventqr_success else R.color.eventqr_muted))
    }

    private fun updateStrengthUI(metCount: Int) {
        val (colorRes, label) = when (metCount) {
            0 -> R.color.eventqr_muted to ""
            1 -> R.color.eventqr_error to "Weak"
            2 -> R.color.eventqr_warning to "Fair"
            3 -> R.color.eventqr_info to "Good"
            4 -> R.color.eventqr_success to "Strong"
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
