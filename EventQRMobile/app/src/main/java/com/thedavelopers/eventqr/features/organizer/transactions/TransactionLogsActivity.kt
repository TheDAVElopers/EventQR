package com.thedavelopers.eventqr.features.organizer.transactions

import com.thedavelopers.eventqr.ui.theme.boldSpan
import com.thedavelopers.eventqr.ui.theme.color
import com.thedavelopers.eventqr.ui.theme.dimenFloat
import com.thedavelopers.eventqr.ui.theme.dimenPx
import com.thedavelopers.eventqr.ui.theme.roundedRect
import com.thedavelopers.eventqr.ui.theme.setTextSizeRes
import com.thedavelopers.eventqr.ui.theme.strong
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
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
        showEventsLoadingScreen(getString(R.string.transaction_logs_title))
        lifecycleScope.launch {
            val eventsLoad = repository.loadApprovedOrganizerEvents()
            if (eventsLoad.source == OrganizerMvpDataSource.ERROR) {
                showEventsLoadErrorScreen(getString(R.string.transaction_logs_title), eventsLoad.message) { recreate() }
                return@launch
            }
            val selectableEvents = eventsLoad.data
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
            setTextSizeRes(R.dimen.type_body_medium)
            strong()
            setTextColor(color(R.color.text_primary))
        }

        val arrow = ImageView(this).apply {
            setImageResource(R.drawable.ic_arrow_drop_down)
            setColorFilter(color(R.color.neutral_label))
            contentDescription = getString(R.string.transaction_logs_select_event_cd)
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dimenPx(R.dimen.space_md), dimenPx(R.dimen.space_10), dimenPx(R.dimen.space_lg), dimenPx(R.dimen.space_10))
            minimumHeight = dimenPx(R.dimen.button_primary_height)
            isClickable = approvedEvents.isNotEmpty()
            isFocusable = approvedEvents.isNotEmpty()
            background = roundedRect(color(R.color.surface), R.dimen.radius_14, orgColors.border)
            elevation = dimenPx(R.dimen.elevation_raised).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, 0, 0, dimenPx(R.dimen.space_md)) }

            val iconContainer = LinearLayout(this@TransactionLogsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.icon_tile_md), dimenPx(R.dimen.icon_tile_md))
                background = roundedRect(color(R.color.accent_container), R.dimen.radius_md)

                addView(ImageView(this@TransactionLogsActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.icon_md), dimenPx(R.dimen.icon_md))
                    setImageResource(R.drawable.ic_calendar)
                    setColorFilter(orgColors.primary)
                })
            }
            addView(iconContainer)

            val textContainer = LinearLayout(this@TransactionLogsActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dimenPx(R.dimen.space_md), 0, dimenPx(R.dimen.space_sm), 0)
                }

                addView(TextView(this@TransactionLogsActivity).apply {
                    text = getString(R.string.transaction_logs_event_caption)
                    setTextSizeRes(R.dimen.type_micro)
                    strong()
                    setTextColor(color(R.color.text_label))
                    setLetterSpacing(dimenFloat(R.dimen.tracking_label_05))
                })
                addView(eventTitleText)
            }
            addView(textContainer)

            addView(arrow.apply {
                layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.icon_22), dimenPx(R.dimen.icon_22))
            })
        }

        var popup: PopupWindow? = null
        var isOpen = false

        fun buildDropdown(): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedRect(color(R.color.surface), R.dimen.radius_14, orgColors.border)
            approvedEvents.forEachIndexed { index, event ->
                val selected = index == selectedIndex
                addView(LinearLayout(this@TransactionLogsActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dimenPx(R.dimen.space_lg), dimenPx(R.dimen.space_14), dimenPx(R.dimen.space_lg), dimenPx(R.dimen.space_14))
                    setBackgroundColor(if (selected) color(R.color.accent_container) else color(R.color.surface))
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
                        setTextColor(if (selected) orgColors.primary else color(R.color.text_primary))
                        setTextSizeRes(R.dimen.type_body_medium)
                        strong()
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
                elevation = dimenPx(R.dimen.elevation_overlay).toFloat()
                setBackgroundDrawable(ColorDrawable(color(R.color.transparent)))
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
            ).apply { setMargins(0, dimenPx(R.dimen.space_3), 0, 0) }

            addView(ImageView(this@TransactionLogsActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.icon_14), dimenPx(R.dimen.icon_14))
                setImageResource(iconRes)
                setColorFilter(orgColors.muted)
            })

            addView(TextView(this@TransactionLogsActivity).apply {
                text = textValue
                setTextSizeRes(R.dimen.type_caption)
                setTextColor(orgColors.muted)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { marginStart = dimenPx(R.dimen.space_6) }
            })
        }

    private fun logCard(log: OrganizerMvpTransaction): LinearLayout {
        val status = normalizedStatus(log)
        val isRejected = status.equals("Rejected", ignoreCase = true)

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(dimenPx(R.dimen.space_14), dimenPx(R.dimen.space_14), dimenPx(R.dimen.space_14), dimenPx(R.dimen.space_14))
            background = roundedRect(color(R.color.surface), R.dimen.radius_14, orgColors.border)
            elevation = dimenPx(R.dimen.elevation_raised).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, dimenPx(R.dimen.space_xs), 0, dimenPx(R.dimen.space_sm)) }

            val avatarContainer = LinearLayout(this@TransactionLogsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.icon_tile_md), dimenPx(R.dimen.icon_tile_md))
                background = roundedRect(color(R.color.accent_container), R.dimen.radius_md)

                addView(ImageView(this@TransactionLogsActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.icon_md), dimenPx(R.dimen.icon_md))
                    setImageResource(R.drawable.ic_profile_person)
                    setColorFilter(orgColors.primary)
                })
            }
            addView(avatarContainer)

            val rightColumn = LinearLayout(this@TransactionLogsActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dimenPx(R.dimen.space_md)
                }

                val badgeColor = if (isRejected) color(R.color.danger) else color(R.color.success_dark)
                val badgeBgColor = if (isRejected) color(R.color.error_container) else color(R.color.success_container)
                val badgeIconRes = if (isRejected) R.drawable.ic_row_alert else R.drawable.ic_staff_check

                val statusBadge = LinearLayout(this@TransactionLogsActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dimenPx(R.dimen.space_sm), dimenPx(R.dimen.space_xs), dimenPx(R.dimen.space_10), dimenPx(R.dimen.space_xs))
                    background = roundedRect(badgeBgColor, R.dimen.radius_14)

                    addView(ImageView(this@TransactionLogsActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.icon_14), dimenPx(R.dimen.icon_14))
                        setImageResource(badgeIconRes)
                        setColorFilter(badgeColor)
                    })

                    addView(TextView(this@TransactionLogsActivity).apply {
                        text = status
                        setTextSizeRes(R.dimen.type_caption)
                        strong()
                        setTextColor(badgeColor)
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ).apply { marginStart = dimenPx(R.dimen.space_xs) }
                    })
                }

                val chevron = ImageView(this@TransactionLogsActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.icon_18), dimenPx(R.dimen.icon_18)).apply { marginStart = dimenPx(R.dimen.space_6) }
                    setImageResource(R.drawable.ic_chevron_right)
                    setColorFilter(color(R.color.neutral))
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
                        setTextSizeRes(R.dimen.type_body)
                        strong()
                        setTextColor(color(R.color.text_primary))
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
                    setTextSizeRes(R.dimen.type_caption)
                    strong()
                    setTextColor(if (isRejected) color(R.color.danger) else color(R.color.success_dark))
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = dimenPx(R.dimen.space_xxs) }
                }
                addView(subtitleText)

                val detailsColumn = LinearLayout(this@TransactionLogsActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = dimenPx(R.dimen.space_6) }

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
                        setPadding(dimenPx(R.dimen.space_10), dimenPx(R.dimen.space_6), dimenPx(R.dimen.space_10), dimenPx(R.dimen.space_6))
                        // the old FEE2E2 hairline vanished into the merged error_container fill: no stroke
                        background = roundedRect(color(R.color.error_container), R.dimen.radius_sm)
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ).apply { topMargin = dimenPx(R.dimen.space_10) }

                        addView(ImageView(this@TransactionLogsActivity).apply {
                            layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.icon_14), dimenPx(R.dimen.icon_14))
                            setImageResource(R.drawable.ic_row_alert)
                            setColorFilter(color(R.color.danger))
                        })

                        val spannable = SpannableStringBuilder().apply {
                            append(getString(R.string.transaction_logs_reason_label), boldSpan(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                            append(log.reason)
                        }

                        addView(TextView(this@TransactionLogsActivity).apply {
                            text = spannable
                            setTextSizeRes(R.dimen.type_overline)
                            setTextColor(color(R.color.danger))
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                            ).apply { marginStart = dimenPx(R.dimen.space_6) }
                        })
                    }
                    addView(reasonBanner)
                }
            }
            addView(rightColumn)
        }
    }
}
