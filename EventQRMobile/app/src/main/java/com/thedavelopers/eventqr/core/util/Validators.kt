package com.thedavelopers.eventqr.core.util

object Validators {
    data class PasswordRequirements(
        val hasMinLength: Boolean,
        val hasCapital: Boolean,
        val hasSpecial: Boolean,
        val hasSmall: Boolean,
        val hasNumber: Boolean,
    ) {
        val isValid: Boolean
            get() = hasMinLength && hasCapital && hasSpecial && hasSmall && hasNumber
    }

    fun isValidEmail(value: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(value.trim()).matches()
    }

    fun isValidPassword(value: String): Boolean {
        return value.trim().length >= 8
    }

    fun isValidPhoneNumber(value: String): Boolean {
        return value.trim().length >= 7
    }

    fun isNonEmpty(value: String): Boolean {
        return value.trim().isNotEmpty()
    }

    fun passwordRequirements(value: String): PasswordRequirements {
        return PasswordRequirements(
            hasMinLength = value.length >= 8,
            hasCapital = value.any { it.isUpperCase() },
            hasSpecial = value.any { !it.isLetterOrDigit() },
            hasSmall = value.any { it.isLowerCase() },
            hasNumber = value.any { it.isDigit() },
        )
    }

    fun isValidSignUpPassword(value: String): Boolean {
        return passwordRequirements(value).isValid
    }
}
