package com.thedavelopers.eventqr.features.organizer.attendees

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.util.ScreenInsets
import com.thedavelopers.eventqr.features.organizer.EXTRA_EVENT_ID
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpAttendee
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpDataSource
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpEvent
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpTransactionEntry
import com.thedavelopers.eventqr.features.organizer.OrganizerRepository
import com.thedavelopers.eventqr.features.organizer.attendeeInitial
import com.thedavelopers.eventqr.features.organizer.bindStatusBadge
import com.thedavelopers.eventqr.features.organizer.intentEventId
import com.thedavelopers.eventqr.features.organizer.resolveSelectedEvent
import com.thedavelopers.eventqr.features.organizer.saveSelectedEventId
import com.thedavelopers.eventqr.features.organizer.selectedEventId
import com.thedavelopers.eventqr.features.organizer.statusBucket
import com.thedavelopers.eventqr.features.organizer.statusLabel
import com.thedavelopers.eventqr.features.organizer.tintBlock
import com.thedavelopers.eventqr.features.organizer.transactionTypeLabel
import com.thedavelopers.eventqr.features.organizer.transactions.TransactionLogsActivity
import com.thedavelopers.eventqr.ui.icons.AppIcons
import com.thedavelopers.eventqr.ui.theme.color
import com.thedavelopers.eventqr.ui.theme.defaultBold
import com.thedavelopers.eventqr.ui.theme.dimen
import com.thedavelopers.eventqr.ui.theme.dimenOffset
import com.thedavelopers.eventqr.ui.theme.setTextSizeRes
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

open class AttendeeDetailsActivity : AppCompatActivity() {
    private lateinit var repository: OrganizerRepository
    private lateinit var selectedEvent: OrganizerMvpEvent
    private lateinit var attendee: OrganizerMvpAttendee
    private lateinit var skeletonLoading: View
    private val displayZone: ZoneId = ZoneId.of("Asia/Manila")
    private val lastActivityFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.ENGLISH)
    private val registeredDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendee_details)
        ScreenInsets.padContentForNavigationBar(this)
        repository = OrganizerRepository(this)

        findViewById<View>(R.id.topBarBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnAttendeeRetry).setOnClickListener {
            val attendeeId = intent.getStringExtra(SearchAttendeesActivity.EXTRA_ATTENDEE_ID).orEmpty()
            // If the event list itself failed there is no selected event yet: retry that load first.
            if (::selectedEvent.isInitialized) {
                loadAttendee(selectedEvent.id, attendeeId)
            } else {
                loadEventThenAttendee(intentEventId().orEmpty().ifBlank { selectedEventId() }, attendeeId)
            }
        }
        findViewById<View>(R.id.btnViewFullLog).setOnClickListener {
            startActivity(Intent(this, TransactionLogsActivity::class.java).apply {
                putExtra(EXTRA_EVENT_ID, selectedEvent.id)
                putExtra(SearchAttendeesActivity.EXTRA_ATTENDEE_ID, attendee.id)
            })
            saveSelectedEventId(selectedEvent.id)
        }
        skeletonLoading = findViewById(R.id.skeletonLoading)

        val attendeeId = intent.getStringExtra(SearchAttendeesActivity.EXTRA_ATTENDEE_ID).orEmpty()
        val eventId = intentEventId().orEmpty().ifBlank { selectedEventId() }
        if (attendeeId.isBlank() || eventId.isBlank()) {
            Toast.makeText(this, this.getString(R.string.attendee_details_open_attendee_details_from_an_event), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadEventThenAttendee(eventId, attendeeId)
    }

    private fun loadEventThenAttendee(eventId: String, attendeeId: String) {
        findViewById<View>(R.id.layoutAttendeeError).visibility = View.GONE
        skeletonLoading.visibility = View.VISIBLE
        lifecycleScope.launch {
            val eventsLoad = repository.loadApprovedOrganizerEvents()
            if (eventsLoad.source == OrganizerMvpDataSource.ERROR) {
                // A failed fetch is not "event not available": keep the screen and offer Retry.
                showAttendeeError(eventsLoad.message?.takeIf { it.isNotBlank() } ?: getString(R.string.organizer_dashboard_events_load_failed))
                return@launch
            }
            selectedEvent = resolveSelectedEvent(eventsLoad.data, eventId)
                ?: run {
                    Toast.makeText(this@AttendeeDetailsActivity, this@AttendeeDetailsActivity.getString(R.string.attendee_details_selected_event_not_available), Toast.LENGTH_SHORT).show()
                    finish()
                    return@launch
                }
            loadAttendee(eventId, attendeeId)
        }
    }

    private fun loadAttendee(eventId: String, attendeeId: String) {
        findViewById<View>(R.id.layoutAttendeeError).visibility = View.GONE
        skeletonLoading.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val attendeeLoad = repository.loadAttendeesForMvp(eventId)
                if (attendeeLoad.source == OrganizerMvpDataSource.ERROR) {
                    showAttendeeError(attendeeLoad.message ?: getString(R.string.attendee_details_record_load_failed))
                    return@launch
                }
                attendee = attendeeLoad.data.firstOrNull { it.id == attendeeId } ?: run {
                    showAttendeeError(getString(R.string.attendee_details_record_not_found))
                    return@launch
                }
                renderProfile()
                renderDetails()
            } catch (error: Exception) {
                showAttendeeError(error.message ?: getString(R.string.attendee_details_record_load_failed))
            }
        }
    }

    private fun showAttendeeError(message: String) {
        skeletonLoading.visibility = View.GONE
        findViewById<View>(R.id.layoutAttendeeError).visibility = View.VISIBLE
        findViewById<TextView>(R.id.txtAttendeeError).text = message
    }

    private fun renderProfile() {
        skeletonLoading.visibility = View.GONE
        findViewById<TextView>(R.id.topBarTitle).text = getString(R.string.attendee_details_attendee_details)
        findViewById<TextView>(R.id.txtDetailInitial).text = attendeeInitial(attendee.name)
        findViewById<TextView>(R.id.txtDetailName).text = attendee.name
        findViewById<TextView>(R.id.txtDetailEmail).text = attendee.email
        findViewById<TextView>(R.id.txtDetailPhone).text = attendee.phone
        findViewById<View>(R.id.cardAttendeeProfile).visibility = View.VISIBLE
    }

    private fun renderDetails() {
        findViewById<TextView>(R.id.txtDetailEventValue).text = selectedEvent.title
        findViewById<TextView>(R.id.txtDetailRegistrationIdValue).text =
            attendee.registrationNumber?.toString() ?: getString(R.string.ui_organizer_value_unavailable)
        findViewById<TextView>(R.id.txtDetailStatusValue).apply {
            attendee.bindStatusBadge(this)
        }
        findViewById<TextView>(R.id.txtDetailIdStatusValue).apply {
            val issued = attendee.qrCredentialStatus.equals("Issued", ignoreCase = true)
            text = getString(if (issued) R.string.attendee_details_id_issued else R.string.attendee_details_no_id)
            // `Issued` is the only confirmation on this card, so it is the only lime badge.
            val bg = if (issued) R.color.ui_accent else R.color.ui_surface_sunken
            val fg = if (issued) R.color.ui_on_accent else R.color.ui_text_secondary
            backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(bg))
            setTextColor(getColor(fg))
        }
        findViewById<TextView>(R.id.txtDetailRegisteredValue).text = formatRegisteredDate(attendee.registeredDate)
        findViewById<TextView>(R.id.txtDetailLastActivityValue).text = formatLastActivity(attendee.lastTransactionTime)
        findViewById<TextView>(R.id.txtDetailPointsValue).text = getString(R.string.attendee_details_points_value, attendee.points)
        renderTransactionHistory()
        findViewById<View>(R.id.cardAttendeeDetails).visibility = View.VISIBLE
    }

    private fun renderTransactionHistory() {
        val container = findViewById<LinearLayout>(R.id.transactionHistoryContainer)
        val emptyText = findViewById<TextView>(R.id.txtNoTransactions)
        container.removeAllViews()
        if (attendee.recentTransactions.isEmpty()) {
            emptyText.visibility = View.VISIBLE
        } else {
            emptyText.visibility = View.GONE
            attendee.recentTransactions
                .sortedWith(compareByDescending<OrganizerMvpTransactionEntry> { parseToLocalDateTime(it.timestamp.orEmpty()) ?: LocalDateTime.MIN })
                .forEach { entry ->
                    val itemBox = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        setPadding(dimenOffset(R.dimen.space_md), dimenOffset(R.dimen.space_md), dimenOffset(R.dimen.space_md), dimenOffset(R.dimen.space_md))
                        tintBlock(R.color.ui_background, R.drawable.bg_ui_block_r10)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(0, dimenOffset(R.dimen.space_xs), 0, dimenOffset(R.dimen.space_xs))
                        }

                        // Timeline Dot + Line
                        val timelineLayout = LinearLayout(this@AttendeeDetailsActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            gravity = android.view.Gravity.CENTER_HORIZONTAL
                            layoutParams = LinearLayout.LayoutParams(dimenOffset(R.dimen.space_md), dimenOffset(R.dimen.icon_tile_sm)).apply {
                                marginEnd = dimenOffset(R.dimen.space_md)
                            }
                            addView(View(this@AttendeeDetailsActivity).apply {
                                layoutParams = LinearLayout.LayoutParams(dimenOffset(R.dimen.dot_sm), dimenOffset(R.dimen.dot_sm))
                                tintBlock(R.color.ui_primary, R.drawable.bg_ui_dot)
                            })
                            addView(View(this@AttendeeDetailsActivity).apply {
                                layoutParams = LinearLayout.LayoutParams(dimenOffset(R.dimen.space_xxs), 0, 1f).apply {
                                    topMargin = dimenOffset(R.dimen.space_xxs)
                                }
                                tintBlock(R.color.ui_hairline, R.drawable.bg_ui_block_r4)
                            })
                        }
                        addView(timelineLayout)

                        // Icon Box
                        val iconBox = android.widget.FrameLayout(this@AttendeeDetailsActivity).apply {
                            layoutParams = LinearLayout.LayoutParams(dimenOffset(R.dimen.icon_tile_md), dimenOffset(R.dimen.icon_tile_md)).apply {
                                marginEnd = dimenOffset(R.dimen.space_md)
                            }
                            tintBlock(R.color.ui_primary_container, R.drawable.bg_ui_block_r10)
                            addView(android.widget.ImageView(this@AttendeeDetailsActivity).apply {
                                layoutParams = android.widget.FrameLayout.LayoutParams(dimenOffset(R.dimen.icon_md), dimenOffset(R.dimen.icon_md), android.view.Gravity.CENTER)
                                setImageResource(AppIcons.Event)
                                imageTintList = android.content.res.ColorStateList.valueOf(color(R.color.ui_primary))
                            })
                        }
                        addView(iconBox)

                        // Text Stack
                        val textStack = LinearLayout(this@AttendeeDetailsActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            val label = transactionTypeLabel(this@AttendeeDetailsActivity, entry.type)
                            val time = formatEntryTimestamp(entry.timestamp)
                            addView(TextView(this@AttendeeDetailsActivity).apply {
                                text = label
                                setTextColor(getColor(R.color.text_primary))
                                setTextSizeRes(R.dimen.type_body_medium)
                                defaultBold()
                            })
                            addView(TextView(this@AttendeeDetailsActivity).apply {
                                text = time ?: "-"
                                setTextColor(getColor(R.color.text_secondary))
                                setTextSizeRes(R.dimen.type_caption)
                                layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.WRAP_CONTENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                ).apply {
                                    topMargin = dimenOffset(R.dimen.space_xxs)
                                }
                            })
                        }
                        addView(textStack)
                    }
                    container.addView(itemBox)
                }
        }
        findViewById<View>(R.id.cardTransactionHistory).visibility = View.VISIBLE
    }

    private fun formatEntryTimestamp(value: String?): String? {
        val cleaned = value?.trim().orEmpty()
        if (cleaned.isBlank() || cleaned == "-") return null
        return parseToLocalDateTime(cleaned)?.format(lastActivityFormatter)?.uppercase(Locale.ENGLISH) ?: cleaned
    }

    private fun formatLastActivity(value: String): String {
        val cleaned = value.trim()
        if (cleaned.isBlank() || cleaned == "-") return getString(R.string.attendee_details_no_activity_yet)
        return parseToLocalDateTime(cleaned)?.format(lastActivityFormatter)?.uppercase(Locale.ENGLISH) ?: cleaned
    }

    private fun formatRegisteredDate(value: String): String {
        val cleaned = value.trim()
        if (cleaned.isBlank()) return "-"
        return parseToLocalDateTime(cleaned)?.format(registeredDateFormatter) ?: cleaned
    }

    private fun parseToLocalDateTime(value: String): LocalDateTime? {
        val normalized = value.replace("•", "").replace("  ", " ").trim().replace(" ", "T")
        return runCatching { Instant.parse(normalized).atZone(displayZone).toLocalDateTime() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(normalized).atZoneSameInstant(displayZone).toLocalDateTime() }.getOrNull()
            ?: runCatching { ZonedDateTime.parse(normalized).withZoneSameInstant(displayZone).toLocalDateTime() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(value.replace("•", "").replace("  ", " ").trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(value.replace("•", "").replace("  ", " ").trim(), DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.ENGLISH)) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(value.replace("•", "").replace("  ", " ").trim(), DateTimeFormatter.ofPattern("MMMM d, yyyy h:mm a", Locale.ENGLISH)) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(value.replace("•", "").replace("  ", " ").trim(), DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a", Locale.ENGLISH)) }.getOrNull()
    }
}
