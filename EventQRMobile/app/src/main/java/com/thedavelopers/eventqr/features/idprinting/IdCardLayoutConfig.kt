package com.thedavelopers.eventqr.features.idprinting

/**
 * Single source of truth for ID card layout proportions.
 *
 * All sizes are defined as ratios of CR80 card dimensions so that both the
 * Organizer Preview (Views/dp) and the Print Renderer (Canvas/pt) produce
 * visually consistent output regardless of their absolute coordinate systems.
 *
 * Canonical reference: IdTemplateSettingsActivity preview (270dp x 429dp card).
 */
object IdCardLayoutConfig {

    // -- CR80 physical card --
    const val CR80_WIDTH_IN = 2.125f
    const val CR80_HEIGHT_IN = 3.375f
    const val CR80_ASPECT = CR80_HEIGHT_IN / CR80_WIDTH_IN // 1.588

    // -- Preview dp size (Organizer screen) --
    const val PREVIEW_WIDTH_DP = 270
    const val PREVIEW_HEIGHT_DP = 429

    // -- Print page (A4 at 72 pt/in) --
    const val PAGE_W_PT = 595
    const val PAGE_H_PT = 842

    // -- Render quality --
    const val RENDER_SCALE = 4 // offscreen bitmap multiplier for crisp PDF text

    // -- Cut-guide bleed as ratio of card width --
    const val CUT_BLEED_RATIO = 0.0392f // 6/153

    // -- Content margin as ratio of card width --
    const val MARGIN_RATIO = 0.0667f // 18dp / 270dp

    // -- QR code size as ratio of card width --
    const val QR_SIZE_RATIO = 0.65f // ~65% of card width for reliable camera scanning

    // -- QR bottom spacing as ratio of card height --
    const val QR_SPACING_RATIO = 0.0280f // 12dp / 429dp

    // -- Label font size as ratio of card height --
    const val LABEL_FONT_RATIO = 0.0210f // 9sp / 429dp

    // -- Value font sizes as ratio of card height --
    const val NAME_FONT_RATIO = 0.0466f // 20sp / 429dp  (attendee name — largest)
    const val EVENT_NAME_FONT_RATIO = 0.0350f // 15sp / 429dp  (event name value)
    const val ROLE_FONT_RATIO = 0.0303f // 13sp / 429dp
    const val ID_FONT_RATIO = 0.0256f // 11sp / 429dp  (attendee ID + event date)

    // -- Field bottom spacing as ratio of card height --
    const val FIELD_SPACING_RATIO = 0.0186f // 8dp / 429dp

    // -- Color palette --
    const val COLOR_MUTED = 0xFF6B7280.toInt()
    const val COLOR_TEXT = 0xFF111827.toInt()
    const val COLOR_BORDER_LIGHT = 0xFF9CA3AF.toInt()

    // -- Locked + optional field keys --
    const val FIELD_QR_CODE = "QR_CODE"
    const val FIELD_ATTENDEE_NAME = "ATTENDEE_NAME"
    const val FIELD_ATTENDEE_ID = "ATTENDEE_ID"
    const val FIELD_ROLE = "ROLE"
    const val FIELD_EVENT_NAME = "EVENT_NAME"
    const val FIELD_EVENT_DATE = "EVENT_DATE"

    /** Canonical rendering order — both surfaces MUST use this. */
    val FIELD_ORDER: List<String> = listOf(
        FIELD_QR_CODE,
        FIELD_ATTENDEE_NAME,
        FIELD_ROLE,
        FIELD_EVENT_NAME,
        FIELD_ATTENDEE_ID,
        FIELD_EVENT_DATE,
    )

    val LOCKED_FIELDS: List<String> = listOf(FIELD_QR_CODE, FIELD_ATTENDEE_NAME)
    val OPTIONAL_FIELDS: List<String> = listOf(FIELD_ROLE, FIELD_EVENT_NAME, FIELD_ATTENDEE_ID, FIELD_EVENT_DATE)

    /** Fields printed when an event has no saved template (or the server can't return one): QR + name. */
    val DEFAULT_VISIBLE_FIELDS: List<String> = LOCKED_FIELDS

    /** Visible fields from a fetched template, falling back to [DEFAULT_VISIBLE_FIELDS] when none were returned. */
    fun visibleFieldsOrDefault(fetched: List<String?>?): List<String> =
        fetched?.filterNotNull()?.takeIf { it.isNotEmpty() } ?: DEFAULT_VISIBLE_FIELDS

    fun displayName(field: String): String = when (field) {
        FIELD_ATTENDEE_ID -> "Attendee ID"
        FIELD_ROLE -> "Role/Type"
        FIELD_EVENT_NAME -> "Event Name"
        FIELD_EVENT_DATE -> "Event Date"
        FIELD_QR_CODE -> "QR Code"
        FIELD_ATTENDEE_NAME -> "Attendee Name"
        else -> field
    }

    fun sampleValue(field: String): String = when (field) {
        FIELD_ATTENDEE_ID -> "12"
        FIELD_ROLE -> "STAFF"
        FIELD_EVENT_NAME -> "Freshman Orientation"
        FIELD_EVENT_DATE -> "Aug 25, 2026"
        else -> ""
    }
}
