package com.thedavelopers.eventqr.features.organizer.events

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.EventStatus
import com.thedavelopers.eventqr.core.util.ScreenInsets
import com.thedavelopers.eventqr.features.events.EventStatusBadgeStyler
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpDataSource
import com.thedavelopers.eventqr.features.organizer.OrganizerRepository
import com.thedavelopers.eventqr.features.organizer.bindEventBadge
import com.thedavelopers.eventqr.features.organizer.dataSourceBanner
import com.thedavelopers.eventqr.features.organizer.emptyState
import com.thedavelopers.eventqr.features.organizer.errorState
import com.thedavelopers.eventqr.features.organizer.formatCount
import com.thedavelopers.eventqr.features.organizer.intentEventId
import com.thedavelopers.eventqr.features.organizer.loadingState
import com.thedavelopers.eventqr.features.organizer.openOrganizerPage
import com.thedavelopers.eventqr.features.organizer.scanpurposes.ManageScanPurposesActivity
import com.thedavelopers.eventqr.features.organizer.staff.ManageUsersActivity
import com.thedavelopers.eventqr.features.organizer.transactions.TransactionRulesActivity
import com.thedavelopers.eventqr.ui.components.ListRowBinder
import com.thedavelopers.eventqr.ui.components.PassHeadBinder
import com.thedavelopers.eventqr.ui.icons.AppIcons
import com.thedavelopers.eventqr.ui.theme.applyEventQrSystemBarAppearance
import kotlinx.coroutines.launch

/**
 * Event management hub (§6.4) - retires Gen C.
 *
 * Identity is a flat `ui_primary` block: status badge, event name in `title_l`, three inline
 * stats in `numeric_m`, then a perforation. Below it one grouped card holds the five management
 * rows with 16dp chevrons. No gradient banner, no 96dp glyph, no per-row hand-rolled card.
 */
open class EventManagementHubActivity : AppCompatActivity() {
    private lateinit var repository: OrganizerRepository
    private lateinit var contentHost: android.widget.LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_management_hub)
        ScreenInsets.padContentForNavigationBar(this)
        applyEventQrSystemBarAppearance(lightStatusBars = false)
        repository = OrganizerRepository(this)

        val topBar = findViewById<View>(R.id.topBar)
        topBar.findViewById<View>(R.id.topBarBack).setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        topBar.findViewById<TextView>(R.id.topBarTitle).text = getString(R.string.ui_organizer_hub_title)

        // The identity block is present in the layout; the loading/error states replace the list only.
        contentHost = findViewById(R.id.emh_header_banner)
        hideManageRows()
    }

    // Reload on every return to this screen (e.g., after saving edits in
    // EditEventDetailsActivity) so the Registered/Capacity/Available summary never shows
    // stale numbers.
    override fun onResume() {
        super.onResume()
        val eventId = intentEventId() ?: return showMissingEvent()
        renderEvent(eventId)
    }

    private fun hideManageRows() {
        listOf(R.id.emh_menu_edit, R.id.emh_menu_staff, R.id.emh_menu_scan, R.id.emh_menu_transaction, R.id.emh_menu_id)
            .forEach { findViewById<View>(it).visibility = View.GONE }
    }

    private fun showMissingEvent() {
        hideManageRows()
        findViewById<View>(R.id.emh_section_title).visibility = View.GONE
        val host = findViewById<android.widget.LinearLayout>(R.id.emh_header_banner)
        host.removeAllViews()
        host.addView(
            emptyState(
                iconRes = AppIcons.Event,
                title = getString(R.string.organizer_no_event_selected),
                subtext = getString(R.string.organizer_event_id_missing),
                actionLabel = getString(R.string.organizer_open_my_events),
                onAction = { openOrganizerPage(ManageEventsActivity::class.java) },
            ),
        )
    }

    private fun renderEvent(eventId: String) {
        lifecycleScope.launch {
            val load = repository.loadEventForMvp(eventId)
            val event = load.data
            if (event == null) {
                if (load.source == OrganizerMvpDataSource.ERROR) {
                    showStateCard(
                        errorState(load.message ?: getString(R.string.event_management_hub_details_load_failed)) { recreate() },
                    )
                } else {
                    showStateCard(
                        emptyState(
                            iconRes = AppIcons.Event,
                            title = getString(R.string.event_management_hub_event_not_found),
                            subtext = getString(R.string.event_management_hub_event_not_available),
                            actionLabel = getString(R.string.organizer_open_my_events),
                            onAction = { openOrganizerPage(ManageEventsActivity::class.java) },
                        ),
                    )
                }
                return@launch
            }

            val registeredCount = event.currentAttendeeCount.coerceAtLeast(0)
            val capacity = event.capacity.coerceAtLeast(0)
            // capacity <= 0 means no cap was recorded (legacy/unlimited). Printing 0 would read as a
            // full event, which is false; match the event details screen's unavailable treatment.
            val capacityText = if (capacity > 0) formatCount(capacity) else getString(R.string.ui_organizer_value_unavailable)
            val availableText = if (capacity > 0) {
                formatCount((capacity - registeredCount).coerceAtLeast(0))
            } else {
                getString(R.string.ui_organizer_value_unavailable)
            }

            findViewById<TextView>(R.id.emh_event_title).text = event.title.ifBlank {
                getString(R.string.organizer_untitled_event)
            }
            findViewById<TextView>(R.id.emh_stat_registered).text = formatCount(registeredCount)
            findViewById<TextView>(R.id.emh_stat_capacity).text = capacityText
            findViewById<TextView>(R.id.emh_stat_available).text = availableText

            // "Upcoming" is a display state derived from the event window; the backend enum has no
            // UPCOMING. Resolve once so the badge and the routing rule always agree.
            val status = EventStatusBadgeStyler.resolve(
                EventStatusBadgeStyler.fromLabel(event.status),
                event.eventStartAt,
                event.eventEndAt,
            )
            findViewById<TextView>(R.id.emh_status_badge).bindEventBadge(status)

            // The editor locks itself for Active/Completed; Cancelled and Rejected have no editable
            // content either, so only these three lifecycle states open the form.
            val canEdit = when (status) {
                EventStatus.APPROVED, EventStatus.DRAFT, EventStatus.PENDING_REVIEW -> true
                else -> false
            }
            bindRow(
                R.id.emh_menu_edit,
                title = getString(if (canEdit) R.string.event_management_hub_edit_event_details else R.string.event_management_hub_view_event_details),
                meta = getString(R.string.event_management_hub_menu_edit_sub),
                icon = if (canEdit) AppIcons.Edit else AppIcons.Request,
                trailing = ListRowBinder.Trailing.CHEVRON,
            ) {
                if (canEdit) {
                    openOrganizerPage(EditEventDetailsActivity::class.java, event.id, event.title)
                } else {
                    openOrganizerPage(OrganizerEventDetailsActivity::class.java, event.id, event.title)
                }
            }
            bindRow(
                R.id.emh_menu_staff,
                title = getString(R.string.event_management_hub_menu_staff),
                meta = getString(R.string.event_management_hub_menu_staff_sub),
                icon = AppIcons.People,
                trailing = ListRowBinder.Trailing.CHEVRON,
            ) { openOrganizerPage(ManageUsersActivity::class.java, event.id, event.title) }
            bindRow(
                R.id.emh_menu_scan,
                title = getString(R.string.event_management_hub_menu_scan),
                meta = getString(R.string.event_management_hub_menu_scan_sub),
                icon = AppIcons.Scan,
                trailing = ListRowBinder.Trailing.CHEVRON,
            ) { openOrganizerPage(ManageScanPurposesActivity::class.java, event.id, event.title) }
            bindRow(
                R.id.emh_menu_transaction,
                title = getString(R.string.event_management_hub_menu_transaction),
                meta = getString(R.string.event_management_hub_menu_transaction_sub),
                icon = AppIcons.Audit,
                trailing = ListRowBinder.Trailing.CHEVRON,
            ) { openOrganizerPage(TransactionRulesActivity::class.java, event.id, event.title) }
            bindRow(
                R.id.emh_menu_id,
                title = getString(R.string.event_management_hub_menu_id),
                meta = getString(R.string.event_management_hub_menu_id_sub),
                icon = AppIcons.Document,
                trailing = ListRowBinder.Trailing.CHEVRON,
            ) {
                openOrganizerPage(
                    com.thedavelopers.eventqr.features.organizer.idtemplate.IdTemplateSettingsActivity::class.java,
                    event.id,
                    event.title,
                )
            }

            dataSourceBanner(load)?.let { banner ->
                val container = findViewById<android.widget.LinearLayout>(R.id.emh_header_banner)
                container.addView(banner)
            }
        }
    }

    private fun bindRow(
        rowId: Int,
        title: String,
        meta: String,
        @androidx.annotation.DrawableRes icon: Int,
        trailing: ListRowBinder.Trailing,
        onClick: () -> Unit,
    ) {
        val row = findViewById<View>(rowId)
        row.visibility = View.VISIBLE
        ListRowBinder.bind(
            row = row,
            title = title,
            meta = meta,
            trailing = trailing,
            showDivider = rowId != R.id.emh_menu_id,
            onClick = onClick,
        )
        row.findViewById<android.widget.ImageView>(R.id.listRowIcon)?.setImageResource(icon)
    }

    private fun showStateCard(state: View) {
        hideManageRows()
        findViewById<View>(R.id.emh_section_title).visibility = View.GONE
        val host = findViewById<android.widget.LinearLayout>(R.id.emh_header_banner)
        host.removeAllViews()
        host.addView(loadingState(getString(R.string.event_management_hub_loading_details)))
        host.addView(state)
    }
}
