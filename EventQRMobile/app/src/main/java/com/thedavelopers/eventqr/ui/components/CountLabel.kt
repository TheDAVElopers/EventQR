package com.thedavelopers.eventqr.ui.components

import java.util.Locale

/**
 * The compact pass head's result count (`R.id.passHeadTitleCount`): `1 EVENT`, `28 EVENTS`,
 * `1.2K EVENTS`, `12K EVENTS`, `1.2M EVENTS`.
 *
 * The title row gives the count a 112dp cap and autosizes it 11sp -> 9sp (`overline` ->
 * `ui_pass_head_count_text_min`), which holds roughly 18 overline characters at the floor. That
 * only works while the label stays short, so the label is capped here, at the data layer: the
 * number part is at most five characters (`9223Q`) and the whole label at most [MAX_LENGTH] for
 * any [value] up to [Long.MAX_VALUE] and a unit of any length - see [format] for the final
 * degradation order when the unit is too long to fit. Autosize then stays a safety net for longer
 * translations instead of the thing that saves a 30-character string.
 *
 * Formatting is locale-independent by construction - no `String.format`, no default locale, the
 * unit uppercased with [Locale.ROOT] - so the label is the same width in every locale.
 */
object CountLabel {

    /** Hard ceiling for [format]: 16 characters, whatever the unit length. */
    const val MAX_LENGTH = 16

    private val SCALES = arrayOf(
        1_000L to "K",
        1_000_000L to "M",
        1_000_000_000L to "B",
        1_000_000_000_000L to "T",
        1_000_000_000_000_000L to "Q",
    )

    /**
     * @param value a count; negative values clamp to 0 (a count is never negative).
     * @param unitSingular the noun after the number, singular; uppercased here and pluralised with
     *   a trailing `S` unless the number renders as exactly `1`, so `event` gives `1 EVENT` and
     *   `28 EVENTS`. A compact number (`1K`, `1.2K`) always takes the plural.
     *
     * The result is at most [MAX_LENGTH] characters for any unit length, and non-empty. When the
     * full `number unit` label does not fit, the parts degrade in this order - FINAL, do not
     * reorder (the literal (a)(b)(c) order would make truncation dead code: dropping the decimal
     * frees only two characters, so the ceiling is always reached at the truncation step):
     * 1. the decimal part of the compact number is dropped (`1.2K EVENTS` -> `1K EVENTS`), which
     *    keeps the whole unit;
     * 2. the unit is truncated to the room the number leaves (`9223Q ATTENDEE`), on a character
     *    boundary, so a surrogate pair - a multi-byte letter - is never cut in half;
     * 3. only when not one character of the unit fits does the unit go entirely, leaving the
     *    compact number alone (`9223Q`). A non-empty unit never yields an empty label.
     */
    fun format(value: Long, unitSingular: String): String {
        val count = value.coerceAtLeast(0L)
        val number = if (count < SCALES[0].first) count.toString() else compact(count)
        val unit = unitSingular.uppercase(Locale.ROOT) + if (number == "1") "" else "S"
        if (number.length <= MAX_LENGTH - (unit.length + 1)) return "$number $unit"

        // 1. drop the decimals: a compact number only, so the unit keeps its room
        val whole = withoutDecimal(number)
        if (whole.length <= MAX_LENGTH - (unit.length + 1)) return "$whole $unit"

        // 2. truncate the unit to the room the number leaves
        val room = MAX_LENGTH - (whole.length + 1)
        if (room < 1) return whole
        val kept = unit.truncatedToCodePointBoundary(room)

        // 3. nothing of the unit fits: the number stands alone
        return if (kept.isEmpty()) whole else "$whole $kept"
    }

    /** `1.2K` -> `1K`. A number without a decimal is returned unchanged. */
    private fun withoutDecimal(number: String): String {
        val dot = number.indexOf('.')
        return if (dot < 0) number else number.removeRange(dot, dot + 2)
    }

    /** At most [limit] characters, without ever splitting a surrogate pair. */
    private fun String.truncatedToCodePointBoundary(limit: Int): String {
        if (length <= limit) return this
        var end = limit
        if (Character.isHighSurrogate(this[end - 1])) end--
        return substring(0, end)
    }

    /**
     * Two significant digits: one decimal below 100 in scale (`1.2K`, `12.3K`), none above
     * (`123K`). A value that rounds up to `1000` in scale is promoted instead of printing four
     * digits, so `999_999` reads `1M` and not `1000K`.
     */
    private fun compact(count: Long): String {
        var index = SCALES.lastIndex
        while (index > 0 && count < SCALES[index].first) index--
        val (divisor, suffix) = SCALES[index]
        val scaled = count.toDouble() / divisor
        val number = if (scaled < 100.0) {
            val tenths = Math.round(scaled * 10)
            if (tenths % 10 == 0L) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
        } else {
            Math.round(scaled).toString()
        }
        return if (number == "1000" && index < SCALES.lastIndex) {
            "1" + SCALES[index + 1].second
        } else {
            number + suffix
        }
    }
}
