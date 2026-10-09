package com.thedavelopers.eventqr.features.organizer.transactions

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.organizer.*
import com.thedavelopers.eventqr.features.organizer.attendees.SearchAttendeesActivity
import kotlinx.coroutines.launch

open class TransactionLogsActivity : AppCompatActivity() {
    private lateinit var repository: OrganizerRepository
    private lateinit var selectedEvent: OrganizerMvpEvent
    private lateinit var list: LinearLayout
    private var attendeeId: String? = null
    private var logsSource: OrganizerMvpLoad<List<OrganizerMvpTransaction>> =
        OrganizerMvpLoad(emptyList(), OrganizerMvpDataSource.ERROR, null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = OrganizerRepository(this)
        val eventId = intentEventId() ?: return showMissingEventScreen(getString(R.string.transaction_logs_title))
        lifecycleScope.launch {
            val selectableEvents = repository.getApprovedOrganizerEvents()
            selectedEvent = resolveSelectedEvent(selectableEvents, eventId) ?: run {
                showMissingEventScreen(getString(R.string.transaction_logs_title))
                return@launch
            }
            attendeeId = intent.getStringExtra(SearchAttendeesActivity.EXTRA_ATTENDEE_ID)
            val content = organizerShell(getString(R.string.transaction_logs_title), null, null, showBack = true)

            if (selectableEvents.isNotEmpty()) {
                content.addView(buildEventSelectorCard(selectableEvents, selectedEvent.id) {
                    selectedEvent = it
                    repository.saveSelectedEventId(it.id)
                    saveSelectedEventId(it.id)
                    loadLogs()
                })
            }

            list = LinearLayout(this@TransactionLogsActivity).apply {
                id = com.thedavelopers.eventqr.R.id.tlg_list
                orientation = LinearLayout.VERTICAL
            }
            content.addView(list)
            list.addView(loadingState(getString(R.string.transaction_logs_loading)))
            loadLogs()
        }
    }

    private fun loadLogs() {
        lifecycleScope.launch {
            logsSource = repository.loadTransactionsForMvp(selectedEvent.id, selectedEvent.title)
            render()
        }
    }

    private fun isApproved(log: OrganizerMvpTransaction): Boolean =
        log.status.equals("Approved", true) || log.status.equals("Successful", true)

    private fun normalizedStatus(log: OrganizerMvpTransaction): String =
        if (isApproved(log)) getString(R.string.common_approved) else log.status

    private fun render() {
        val allLogs = logsSource.data
        val logs = attendeeId?.let { id -> allLogs.filter { it.attendeeId == id } } ?: allLogs
        list.removeAllViews()
        if (allLogs.isEmpty() && logsSource.source == OrganizerMvpDataSource.ERROR) {
            list.addView(errorState(logsSource.message ?: getString(R.string.transaction_logs_load_failed)) { loadLogs() })
            return
        }
        if (logs.isEmpty()) {
            val title = getString(if (attendeeId == null) R.string.transaction_logs_no_event_logs_yet else R.string.transaction_logs_no_attendee_logs_yet)
            val subtext = getString(if (attendeeId == null) R.string.transaction_logs_transaction_logs_will_appear_here_on else R.string.transaction_logs_no_logs_found_for_this_attendee)
            list.addView(emptyState(
                iconRes = R.drawable.ic_organizer_reports,
                title = title,
                subtext = subtext,
            ))
            return
        }
        logs.forEach { list.addView(logCard(it)) }
    }

    private fun buildEventSelectorCard(
        events: List<OrganizerMvpEvent>,
        selectedEventId: String,
        onSelected: (OrganizerMvpEvent) -> Unit,
    ): View {
        val approvedEvents = events.approvedOnly()
        val titles = approvedEvents.map { it.title.ifBlank { getString(R.string.common_untitled_event) } }
        var selectedIndex = approvedEvents.indexOfFirst { it.id == selectedEventId }.takeIf { it >= 0 } ?: 0
        if (approvedEvents.isEmpty()) selectedIndex = -1

        val eventTitleText = TextView(this).apply {
            text = titles.getOrNull(selectedIndex) ?: getString(R.string.user_transaction_history_select_event)
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#111827"))
        }

        val arrow = ImageView(this).apply {
            setImageResource(R.drawable.ic_arrow_drop_down)
            setColorFilter(Color.parseColor("#8E8CAE"))
            contentDescription = getString(R.string.transaction_logs_select_event_cd)
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(16), dp(10))
            minimumHeight = dp(56)
            isClickable = approvedEvents.isNotEmpty()
            isFocusable = approvedEvents.isNotEmpty()
            background = rounded(Color.WHITE, 14, BORDER, density = resources.displayMetrics.density)
            elevation = dp(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, 0, 0, dp(12)) }

            val iconContainer = LinearLayout(this@TransactionLogsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
                background = rounded(Color.parseColor("#EEF2FF"), 12, null, density = resources.displayMetrics.density)

                addView(ImageView(this@TransactionLogsActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
                    setImageResource(R.drawable.ic_calendar)
                    setColorFilter(PURPLE)
                })
            }
            addView(iconContainer)

            val textContainer = LinearLayout(this@TransactionLogsActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(12), 0, dp(8), 0)
                }

                addView(TextView(this@TransactionLogsActivity).apply {
                    text = getString(R.string.transaction_logs_event_caption)
                    textSize = 10f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.parseColor("#8E8CAE"))
                    setLetterSpacing(0.05f)
                })
                addView(eventTitleText)
            }
            addView(textContainer)

            addView(arrow.apply {
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
            })
        }

        var popup: PopupWindow? = null
        var isOpen = false

        fun buildDropdown(): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.WHITE, 14, BORDER, density = resources.displayMetrics.density)
            approvedEvents.forEachIndexed { index, event ->
                val selected = index == selectedIndex
                addView(LinearLayout(this@TransactionLogsActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(16), dp(14), dp(16), dp(14))
                    setBackgroundColor(if (selected) Color.parseColor("#EEF2FF") else Color.WHITE)
                    setOnClickListener {
                        selectedIndex = index
                        eventTitleText.text = titles[index]
                        popup?.dismiss()
                        isOpen = false
                        arrow.rotation = 0f
                        onSelected(event)
                    }
                    addView(TextView(this@TransactionLogsActivity).apply {
                        text = titles[index]
                        setTextColor(if (selected) PURPLE else Color.parseColor("#111827"))
                        textSize = 14f
                        setTypeface(typeface, Typeface.BOLD)
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    })
                })
            }
        }

        fun open() {
            if (approvedEvents.isEmpty()) return
            popup?.dismiss()
            popup = PopupWindow(
                buildDropdown(),
                card.width.takeIf { it > 0 } ?: ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true,
            ).apply {
                isOutsideTouchable = true
                elevation = dp(8).toFloat()
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setOnDismissListener { isOpen = false; arrow.rotation = 0f }
            }
            isOpen = true
            arrow.rotation = 180f
            popup?.showAsDropDown(card, 0, 0)
        }

        card.setOnClickListener {
            if (isOpen) {
                popup?.dismiss()
                isOpen = false
                arrow.rotation = 0f
            } else {
                open()
            }
        }

        return card
    }

    private fun detailRow(iconRes: Int, textValue: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, dp(3), 0, 0) }

            addView(ImageView(this@TransactionLogsActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(14), dp(14))
                setImageResource(iconRes)
                setColorFilter(MUTED)
            })

            addView(TextView(this@TransactionLogsActivity).apply {
                text = textValue
                textSize = 12f
                setTextColor(MUTED)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { marginStart = dp(6) }
            })
        }

    private fun logCard(log: OrganizerMvpTransaction): LinearLayout {
        val status = normalizedStatus(log)
        val isRejected = status.equals("Rejected", ignoreCase = true)

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(Color.WHITE, 14, BORDER, density = resources.displayMetrics.density)
            elevation = dp(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, dp(4), 0, dp(8)) }

            val avatarContainer = LinearLayout(this@TransactionLogsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
                background = rounded(Color.parseColor("#EEF2FF"), 12, null, density = resources.displayMetrics.density)

                addView(ImageView(this@TransactionLogsActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
                    setImageResource(R.drawable.ic_profile_person)
                    setColorFilter(PURPLE)
                })
            }
            addView(avatarContainer)

            val rightColumn = LinearLayout(this@TransactionLogsActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(12)
                }

                val badgeColor = if (isRejected) Color.parseColor("#EF4444") else Color.parseColor("#10B981")
                val badgeBgColor = if (isRejected) Color.parseColor("#FEE2E2") else Color.parseColor("#D1FAE5")
                val badgeIconRes = if (isRejected) R.drawable.ic_row_alert else R.drawable.ic_staff_check

                val statusBadge = LinearLayout(this@TransactionLogsActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(8), dp(4), dp(10), dp(4))
                    background = rounded(badgeBgColor, 14, null, density = resources.displayMetrics.density)

                    addView(ImageView(this@TransactionLogsActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(dp(14), dp(14))
                        setImageResource(badgeIconRes)
                        setColorFilter(badgeColor)
                    })

                    addView(TextView(this@TransactionLogsActivity).apply {
                        text = status
                        textSize = 12f
                        setTypeface(typeface, Typeface.BOLD)
                        setTextColor(badgeColor)
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ).apply { marginStart = dp(4) }
                    })
                }

                val chevron = ImageView(this@TransactionLogsActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginStart = dp(6) }
                    setImageResource(R.drawable.ic_chevron_right)
                    setColorFilter(Color.parseColor("#9CA3AF"))
                }

                val topRow = LinearLayout(this@TransactionLogsActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )

                    addView(TextView(this@TransactionLogsActivity).apply {
                        text = log.attendeeName
                        textSize = 15f
                        setTypeface(typeface, Typeface.BOLD)
                        setTextColor(Color.parseColor("#111827"))
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    })

                    val badgeAndChevron = LinearLayout(this@TransactionLogsActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        addView(statusBadge)
                        addView(chevron)
                    }
                    addView(badgeAndChevron)
                }
                addView(topRow)

                val subtitleText = TextView(this@TransactionLogsActivity).apply {
                    text = getString(R.string.transaction_logs_type_status, transactionTypeLabel(this@TransactionLogsActivity, log.type), status)
                    textSize = 12f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(if (isRejected) Color.parseColor("#EF4444") else Color.parseColor("#10B981"))
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = dp(2) }
                }
                addView(subtitleText)

                val detailsColumn = LinearLayout(this@TransactionLogsActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = dp(6) }

                    addView(detailRow(R.drawable.ic_calendar, getString(R.string.transaction_logs_detail_event, log.eventTitle)))
                    addView(detailRow(R.drawable.ic_profile_person, getString(R.string.transaction_logs_detail_attendee, log.attendeeEmail.ifBlank { getString(R.string.transaction_logs_no_email) })))

                    val staffText = if (log.staffEmail.isNotBlank() && log.staffEmail != "No email" && log.staffEmail != log.staffName) {
                        getString(R.string.transaction_logs_detail_staff_email, log.staffName, log.staffEmail)
                    } else {
                        getString(R.string.transaction_logs_detail_staff, log.staffName)
                    }
                    addView(detailRow(R.drawable.ic_group, staffText))

                    val timeText = if (log.timestamp.startsWith(getString(R.string.transaction_logs_scanned_at_prefix), ignoreCase = true)) {
                        log.timestamp
                    } else {
                        getString(R.string.transaction_logs_detail_scanned_at, log.timestamp)
                    }
                    addView(detailRow(R.drawable.ic_row_clock, timeText))
                }
                addView(detailsColumn)

                if (isRejected && log.reason.isNotBlank()) {
                    val reasonBanner = LinearLayout(this@TransactionLogsActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(10), dp(6), dp(10), dp(6))
                        background = rounded(
                            Color.parseColor("#FEF2F2"),
                            8,
                            strokeColor = Color.parseColor("#FEE2E2"),
                            strokeDp = 1,
                            density = resources.displayMetrics.density
                        )
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ).apply { topMargin = dp(10) }

                        addView(ImageView(this@TransactionLogsActivity).apply {
                            layoutParams = LinearLayout.LayoutParams(dp(14), dp(14))
                            setImageResource(R.drawable.ic_row_alert)
                            setColorFilter(Color.parseColor("#EF4444"))
                        })

                        val spannable = SpannableStringBuilder().apply {
                            append(getString(R.string.transaction_logs_reason_label), StyleSpan(Typeface.BOLD), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                            append(log.reason)
                        }

                        addView(TextView(this@TransactionLogsActivity).apply {
                            text = spannable
                            textSize = 11f
                            setTextColor(Color.parseColor("#EF4444"))
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                            ).apply { marginStart = dp(6) }
                        })
                    }
                    addView(reasonBanner)
                }
            }
            addView(rightColumn)
        }
    }
}
