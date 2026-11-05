package com.thedavelopers.eventqr.ui.components

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * The count label is capped at the data layer so the compact pass head's 112dp slot can never need
 * the autosize floor to rescue it. These are plain JVM tests: no Robolectric, no native graphics,
 * no fonts.
 */
class CountLabelTest {

    @Test
    fun boundaries() {
        val cases = listOf(
            0L to "0 EVENTS",
            1L to "1 EVENT",
            2L to "2 EVENTS",
            999L to "999 EVENTS",
            1_000L to "1K EVENTS",
            1_049L to "1K EVENTS",
            1_050L to "1.1K EVENTS",
            9_999L to "10K EVENTS",
            99_999L to "100K EVENTS",
            100_000L to "100K EVENTS",
            999_999L to "1M EVENTS",
            1_000_000L to "1M EVENTS",
            1_200_000L to "1.2M EVENTS",
            Long.MAX_VALUE to "9223Q EVENTS",
        )
        for ((value, expected) in cases) {
            assertEquals("$value", expected, CountLabel.format(value, "event"))
        }
    }

    /** Literal `1` is the only singular form; a compact `1K` or `1.2K` still takes the plural. */
    @Test
    fun singularOnlyAtExactlyOne() {
        assertEquals("1 EVENT", CountLabel.format(1L, "event"))
        for (value in listOf(0L, 2L, 999L, 1_000L, 1_050L, 1_200L, 999_999L, 1_000_000L, Long.MAX_VALUE)) {
            val label = CountLabel.format(value, "event")
            assertTrue("$value -> $label must be plural", label.endsWith("S"))
        }
        assertEquals("1K EVENTS", CountLabel.format(1_000L, "event"))
        assertEquals("1.2K EVENTS", CountLabel.format(1_200L, "event"))
    }

    /** The documented ceiling: five-character number, space, nine-character singular unit + `S`. */
    @Test
    fun longestSupportedUnitStillFits() {
        val label = CountLabel.format(Long.MAX_VALUE, "attendees")
        assertEquals("9223Q ATTENDEESS", label)
        assertEquals(CountLabel.MAX_LENGTH, label.length)
    }

    @Test
    fun defaults() {
        assertEquals("the unit is uppercased, not trusted", "1K EVENTS", CountLabel.format(1_000L, "event"))
        assertEquals("no decimals below 1000", "42 EVENTS", CountLabel.format(42L, "event"))
        assertEquals("a count is never negative", "0 EVENTS", CountLabel.format(-1L, "event"))
    }

    /**
     * The ceiling holds for any unit length, not only the nine-character one that happens to fit:
     * 1, 9, 10, 11, 40 and 120 characters, at the values that produce the longest numbers.
     */
    @Test
    fun anyUnitLengthStaysInsideTheCeiling() {
        val units = UNITS
        val values = listOf(0L, 1L, 1_050L, 1_000_000L, Long.MAX_VALUE)
        for (unit in units) {
            for (value in values) {
                val label = CountLabel.format(value, unit)
                assertTrue("$value/${unit.length} -> '$label' is too long", label.length <= CountLabel.MAX_LENGTH)
                assertTrue("$value/${unit.length} -> '$label' is empty", label.isNotEmpty())
                assertEquals("$value/${unit.length} -> '$label' has padding", label, label.trim())
            }
        }
    }

    /**
     * The documented degradation order: full label, decimal dropped, unit truncated, unit dropped.
     * Each case pins the step it exercises; the last one pins the surrogate boundary.
     */
    @Test
    fun degradationOrder() {
        // the full label fits, nothing degrades
        assertEquals("0 AS", CountLabel.format(0L, "a"))
        assertEquals("9223Q ATTENDEESS", CountLabel.format(Long.MAX_VALUE, "attendees"))

        // 1: the decimal goes, so an 11-character unit still fits whole
        assertEquals("1K ABCDEFGHIJKS", CountLabel.format(1_050L, "abcdefghijk"))

        // 2: the unit is truncated to the ten characters `9223Q` leaves
        assertEquals("9223Q ABCDEFGHIJ", CountLabel.format(Long.MAX_VALUE, "abcdefghijk"))
        assertEquals("9223Q AAAAAAAAAA", CountLabel.format(Long.MAX_VALUE, "a".repeat(120)))
        assertEquals("1K AAAAAAAAAAAAA", CountLabel.format(1_050L, "a".repeat(40)))

        // 3: a surrogate pair is dropped whole, never halved; the label stays under the ceiling
        val astral = CountLabel.format(Long.MAX_VALUE, "abcdefghi\uD83D\uDE00z")
        assertEquals("9223Q ABCDEFGHI", astral)
        assertTrue("half a surrogate pair", astral.none { it.isSurrogate() })
    }

    /**
     * The hard ceiling, for every input: the label is at most [CountLabel.MAX_LENGTH] characters
     * and never depends on the default locale (`1.2K` must not become `1,2K`).
     */
    @Test
    fun everyValueFitsAndIgnoresTheDefaultLocale() {
        val original = Locale.getDefault()
        val random = Random(20261103)
        val values = buildList {
            addAll(listOf(0L, 1L, 2L, 999L, 1_000L, 1_049L, 1_050L, 9_999L, 999_999L, 1_000_000L, Long.MAX_VALUE))
            repeat(20_000) { add(random.nextLong(0L, Long.MAX_VALUE)) }
        }
        try {
            for (locale in listOf(Locale.GERMANY, Locale.forLanguageTag("tr-TR"))) {
                Locale.setDefault(locale)
                for (unit in listOf("event", "request", "account")) {
                    val shape = Regex("^\\d+(\\.\\d)?[KMBTQ]? ${unit.uppercase(Locale.ROOT)}S?$")
                    for (value in values) {
                        val label = CountLabel.format(value, unit)
                        assertTrue("$value -> $label is too long", label.length <= CountLabel.MAX_LENGTH)
                        assertTrue("$value -> $label is not a compact label", shape.matches(label))
                        assertEquals("$value -> $label plural mismatch", value != 1L, label.endsWith("S"))
                    }
                }
            }
        } finally {
            Locale.setDefault(original)
        }
    }

    private companion object {
        /** Singular units of 1, 9, 10, 11, 40 and 120 characters. */
        val UNITS = listOf(
            "a",
            "attendees",
            "abcdefghij",
            "abcdefghijk",
            "a".repeat(40),
            "a".repeat(120),
        )
    }
}
