package com.thedavelopers.eventqr.core.util

/**
 * Free-form phone fields whose hint reads "+63 912 345 6789": users type the number with spaces or
 * dashes, so separators are dropped before the shared [Validators.isValidPhoneNumber] rule runs.
 */
object PhoneInput {
    private const val SEPARATORS = "-()."

    /** The typed value without whitespace, dashes, dots or parentheses; a leading "+" is kept. */
    fun stripSeparators(raw: String): String =
        raw.trim().filterNot { it.isWhitespace() || it in SEPARATORS }

    /** The E.164 form (+639171234567) when the typed value passes [Validators.isValidPhoneNumber], else null. */
    fun toE164OrNull(raw: String): String? {
        val cleaned = stripSeparators(raw)
        if (!Validators.isValidPhoneNumber(cleaned)) return null
        return "+" + cleaned.removePrefix("+")
    }
}
