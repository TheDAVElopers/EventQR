package com.thedavelopers.eventqr.core.util

object Validators {
    data class PasswordRequirements(
        val hasMinLength: Boolean,
        val hasCapital: Boolean,
        val hasLowercase: Boolean,
        val hasSpecial: Boolean,
        val hasNumber: Boolean,
        /** BCrypt rejects passwords over 72 UTF-8 bytes; not shown as a checklist row. */
        val withinMaxLength: Boolean = true,
    ) {
        val isValid: Boolean
            get() = hasMinLength && hasCapital && hasLowercase && hasSpecial && hasNumber && withinMaxLength

        /** Every checklist rule met; only the byte-length cap may fail. */
        val isOtherwiseValid: Boolean
            get() = hasMinLength && hasCapital && hasLowercase && hasSpecial && hasNumber

        /** Strength meter level 0..4: any 5 met rules reach 4 only when every rule is met. */
        val strengthLevel: Int
            get() {
                val met = listOf(hasMinLength, hasCapital, hasLowercase, hasNumber, hasSpecial).count { it }
                return if (isValid) 4 else minOf(met, 3)
            }
    }

    /**
     * Error code presenters pass to views for a rejected phone number; views map it to
     * R.string.error_invalid_phone so the visible text lives only in strings.xml.
     */
    /** Error code for a password over [MAX_PASSWORD_BYTES]; views map it to R.string.error_password_too_long. */
    const val PASSWORD_TOO_LONG_ERROR = "password_too_long"

    const val MAX_PASSWORD_BYTES = 72

    const val PHONE_ERROR = "invalid_phone"

    fun isValidEmail(value: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(value.trim()).matches()
    }

    fun isValidPhoneNumber(value: String): Boolean {
        // Accepts E.164 (+639171234567) and legacy 639171234567 forms.
        val cleaned = value.trim().removePrefix("+")
        return cleaned.startsWith("63") && cleaned.length == 12 && cleaned.all { it.isDigit() }
    }

    fun isNonEmpty(value: String): Boolean {
        return value.trim().isNotEmpty()
    }

    /**
     * Walks code points (not UTF-16 units) so supplementary characters such as emoji count once and are
     * classified like the backend's PasswordValidator does.
     */
    private inline fun anyCodePoint(value: String, predicate: (Int) -> Boolean): Boolean {
        var i = 0
        while (i < value.length) {
            val cp = value.codePointAt(i)
            if (predicate(cp)) return true
            i += Character.charCount(cp)
        }
        return false
    }

    fun passwordRequirements(value: String): PasswordRequirements {
        return PasswordRequirements(
            hasMinLength = value.codePointCount(0, value.length) >= 8,
            hasCapital = anyCodePoint(value) { Character.isUpperCase(it) },
            hasLowercase = anyCodePoint(value) { Character.isLowerCase(it) },
            hasSpecial = anyCodePoint(value) { !Character.isLetterOrDigit(it) },
            hasNumber = anyCodePoint(value) { Character.isDigit(it) },
            withinMaxLength = value.toByteArray(Charsets.UTF_8).size <= MAX_PASSWORD_BYTES,
        )
    }

    fun isValidSignUpPassword(value: String): Boolean {
        return passwordRequirements(value).isValid
    }
}
