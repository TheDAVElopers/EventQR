package com.thedavelopers.eventqr.features.organizer.attendees

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.organizer.EXTRA_EVENT_ID
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpAttendee
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpDataSource
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpEvent
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpTransactionEntry
import com.thedavelopers.eventqr.features.organizer.OrganizerRepository
import com.thedavelopers.eventqr.features.organizer.saveSelectedEventId
import com.thedavelopers.eventqr.features.organizer.transactions.TransactionLogsActivity
import com.thedavelopers.eventqr.features.organizer.attendeeInitial
import com.thedavelopers.eventqr.features.organizer.intentEventId
import com.thedavelopers.eventqr.features.organizer.resolveSelectedEvent
import com.thedavelopers.eventqr.features.organizer.selectedEventId
import com.thedavelopers.eventqr.features.organizer.statusBucket
import com.thedavelopers.eventqr.features.organizer.statusPalette
import com.thedavelopers.eventqr.features.organizer.transactionTypeLabel
import kotlinx.coroutines.MainScope
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

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
        repository = OrganizerRepository(this)

        findViewById<ImageButton>(R.id.nav_header_back).setOnClickListener { finish() }
        findViewById<View>(R.id.btnAttendeeRetry).setOnClickListener {
            val eventId = selectedEvent.id
            val attendeeId = intent.getStringExtra(SearchAttendeesActivity.EXTRA_ATTENDEE_ID).orEmpty()
            loadAttendee(eventId, attendeeId)
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

        lifecycleScope.launch {
            selectedEvent = resolveSelectedEvent(repository.getApprovedOrganizerEvents(), eventId)
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
        MainScope().launch {
            try {
                val attendeeLoad = repository.loadAttendeesForMvp(eventId)
                if (attendeeLoad.source == OrganizerMvpDataSource.ERROR) {
                    showAttendeeError(attendeeLoad.message ?: "Attendee record could not be loaded.")
                    return@launch
                }
                attendee = attendeeLoad.data.firstOrNull { it.id == attendeeId } ?: run {
                    showAttendeeError("Attendee record not found for this event.")
                    return@launch
                }
                renderProfile()
                renderDetails()
            } catch (error: Exception) {
                showAttendeeError(error.message ?: "Attendee record could not be loaded.")
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
        findViewById<TextView>(R.id.nav_header_title).text = getString(R.string.attendee_details_attendee_details)
        findViewById<TextView>(R.id.txtDetailInitial).text = attendeeInitial(attendee.name)
        findViewById<TextView>(R.id.txtDetailName).text = attendee.name
        findViewById<TextView>(R.id.txtDetailEmail).text = attendee.email
        findViewById<TextView>(R.id.txtDetailPhone).text = attendee.phone
        findViewById<View>(R.id.cardAttendeeProfile).visibility = View.VISIBLE
    }

    private fun renderDetails() {
        findViewById<TextView>(R.id.txtDetailEventValue).text = selectedEvent.title
        findViewById<TextView>(R.id.txtDetailRegistrationIdValue).text = formatRegistrationId(attendee.id)
        findViewById<TextView>(R.id.txtDetailStatusValue).apply {
            text = attendee.statusBucket()
            val (bgColor, textColor) = attendee.statusPalette(this@AttendeeDetailsActivity)
            background = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = 999f
            }
            setTextColor(textColor)
        }
        findViewById<TextView>(R.id.txtDetailIdStatusValue).apply {
            val issued = attendee.qrCredentialStatus.equals("Issued", ignoreCase = true)
            text = getString(if (issued) R.string.attendee_details_id_issued else R.string.attendee_details_no_id)
            background = GradientDrawable().apply {
                setColor(getColor(if (issued) R.color.eventqr_badge_entered_bg else R.color.eventqr_badge_default_bg))
                cornerRadius = 999f
            }
            setTextColor(getColor(if (issued) R.color.eventqr_badge_entered_text else R.color.eventqr_badge_default_text))
        }
        findViewById<TextView>(R.id.txtDetailRegisteredValue).text = formatRegisteredDate(attendee.registeredDate)
        findViewById<TextView>(R.id.txtDetailLastActivityValue).text = formatLastActivity(attendee.lastTransactionTime)
        findViewById<TextView>(R.id.txtDetailPointsValue).text = "${attendee.points} pts"
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
                        setPadding(dp(12), dp(12), dp(12), dp(12))
                        background = GradientDrawable().apply {
                            setColor(0xFFF8FAFC.toInt())
                            cornerRadius = dp(12).toFloat()
                        }
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(0, dp(4), 0, dp(4))
                        }

                        // Timeline Dot + Line
                        val timelineLayout = LinearLayout(this@AttendeeDetailsActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            gravity = android.view.Gravity.CENTER_HORIZONTAL
                            layoutParams = LinearLayout.LayoutParams(dp(12), dp(36)).apply {
                                marginEnd = dp(12)
                            }
                            addView(View(this@AttendeeDetailsActivity).apply {
                                layoutParams = LinearLayout.LayoutParams(dp(8), dp(8))
                                background = GradientDrawable().apply {
                                    shape = GradientDrawable.OVAL
                                    setColor(0xFF635BFF.toInt())
                                }
                            })
                            addView(View(this@AttendeeDetailsActivity).apply {
                                layoutParams = LinearLayout.LayoutParams(dp(2), 0, 1f).apply {
                                    topMargin = dp(2)
                                }
                                background = GradientDrawable().apply {
                                    setColor(0xFFCBD5E1.toInt())
                                }
                            })
                        }
                        addView(timelineLayout)

                        // Icon Box
                        val iconBox = android.widget.FrameLayout(this@AttendeeDetailsActivity).apply {
                            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply {
                                marginEnd = dp(12)
                            }
                            background = GradientDrawable().apply {
                                setColor(0xFFEEF2FF.toInt())
                                cornerRadius = dp(10).toFloat()
                            }
                            addView(android.widget.ImageView(this@AttendeeDetailsActivity).apply {
                                layoutParams = android.widget.FrameLayout.LayoutParams(dp(20), dp(20), android.view.Gravity.CENTER)
                                setImageResource(R.drawable.ic_calendar)
                                imageTintList = android.content.res.ColorStateList.valueOf(0xFF635BFF.toInt())
                            })
                        }
                        addView(iconBox)

                        // Text Stack
                        val textStack = LinearLayout(this@AttendeeDetailsActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            val label = transactionTypeLabel(entry.type)
                            val time = formatEntryTimestamp(entry.timestamp)
                            addView(TextView(this@AttendeeDetailsActivity).apply {
                                text = label
                                setTextColor(getColor(R.color.text_primary))
                                textSize = 14f
                                typeface = android.graphics.Typeface.DEFAULT_BOLD
                            })
                            addView(TextView(this@AttendeeDetailsActivity).apply {
                                text = time ?: "-"
                                setTextColor(getColor(R.color.text_secondary))
                                textSize = 12f
                                layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.WRAP_CONTENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                ).apply {
                                    topMargin = dp(2)
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun formatRegistrationId(value: String): String {
        val cleaned = value.trim()
        if (cleaned.isBlank()) return "-"
        if (cleaned.length <= 12) return cleaned
        return "REG-${cleaned.take(8).uppercase(Locale.ENGLISH)}"
    }

    private fun formatLastActivity(value: String): String {
        val cleaned = value.trim()
        if (cleaned.isBlank() || cleaned == "-") return "No activity yet"
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