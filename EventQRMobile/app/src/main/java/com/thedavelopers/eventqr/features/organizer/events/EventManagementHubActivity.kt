package com.thedavelopers.eventqr.features.organizer.events

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.thedavelopers.eventqr.features.organizer.*
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

open class EventManagementHubActivity : AppCompatActivity() {
    private lateinit var repository: OrganizerRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = OrganizerRepository(this)
        val eventId = intentEventId() ?: return showMissingEventScreen("Event Management")
        val content = organizerShell("Event Management", showBack = true)
        content.addView(loadingState("Loading event details..."))

        MainScope().launch {
            val load = repository.loadEventForMvp(eventId)
            val event = load.data
            content.removeAllViews()
            dataSourceBanner(load)?.let { content.addView(it) }
            if (event == null) {
                content.addView(
                    if (load.source == OrganizerMvpDataSource.ERROR) {
                        errorState(load.message ?: "Event details could not be loaded.") { recreate() }
                    } else {
                        emptyState("Event not found or not available for organizer management.", "Open My Events") {
                            openOrganizerPage(ManageEventsActivity::class.java)
                        }
                    },
                )
                return@launch
            }

            val registeredCount = event.currentAttendeeCount.coerceAtLeast(0)
            val capacity = event.capacity.coerceAtLeast(0)
            val available = (capacity - registeredCount).coerceAtLeast(0)

            // Full-width event banner below top bar
            content.addView(LinearLayout(this@EventManagementHubActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(120),
                ).apply {
                    setMargins(-dp(16), -dp(16), -dp(16), 0)
                }
                // Purple gradient: left #5A45F2, right #9B8CF5
                background = GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(Color.parseColor("#5A45F2"), Color.parseColor("#9B8CF5"))
                )
                setPadding(dp(20), 0, dp(20), 0)
                gravity = android.view.Gravity.CENTER_VERTICAL

                // Status pill
                addView(text(event.lifecycleStatus(), 11, true, Color.parseColor("#5A45F2")).apply {
                    setPadding(dp(12), dp(4), dp(12), dp(4))
                    background = rounded(Color.WHITE, 16, null, density = resources.displayMetrics.density)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    )
                })
                
                // Event title
                addView(text(event.title, 21, true, Color.WHITE).apply {
                    setPadding(0, dp(8), 0, 0)
                })
            })

            content.addView(row().apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = dp(8)
                }
                addView(summaryCard("Registered", formatCount(registeredCount)))
                addView(summaryCard("Capacity", formatCount(capacity), Color.parseColor("#94A3B8")))
                addView(summaryCard("Available", formatCount(available), SUCCESS))
            })

            content.addView(section("Event Management").apply { 
                setPadding(dp(2), dp(20), dp(2), dp(10))
            })
            
            val menuItems = listOf(
                Triple("Staff Assignment", com.thedavelopers.eventqr.R.drawable.ic_admin_users, ManageUsersActivity::class.java),
                Triple("Scan Purposes", com.thedavelopers.eventqr.R.drawable.ic_qr_scan, ManageScanPurposesActivity::class.java),
                Triple("Transaction Rules", com.thedavelopers.eventqr.R.drawable.ic_admin_shield, TransactionRulesActivity::class.java),
                Triple("ID Template", com.thedavelopers.eventqr.R.drawable.ic_file, IdTemplatePlaceholderActivity::class.java),
                Triple("Rewards", com.thedavelopers.eventqr.R.drawable.ic_gift, ManageRewardsActivity::class.java),
                Triple("Point Rules", com.thedavelopers.eventqr.R.drawable.ic_trend_up, PointRulesPlaceholderActivity::class.java),
            )

            menuItems.forEachIndexed { index, (label, icon, target) ->
                val (iconTint, iconBg) = when (index) {
                    0 -> Color.parseColor("#4F46E5") to Color.parseColor("#E0E7FF")
                    1 -> Color.parseColor("#7C3AED") to Color.parseColor("#EDE9FE")
                    2 -> Color.parseColor("#06B6D4") to Color.parseColor("#CFFAFE")
                    3 -> Color.parseColor("#F59E0B") to Color.parseColor("#FEF3C7")
                    4 -> Color.parseColor("#10B981") to Color.parseColor("#D1FAE5")
                    else -> Color.parseColor("#EF4444") to Color.parseColor("#FEE2E2")
                }
                content.addView(
                    menuCard(
                        label = label,
                        iconRes = icon,
                        iconTint = iconTint,
                        iconBg = iconBg,
                    ) { openOrganizerPage(target, event.id, event.title) },
                )
            }
        }
    }
}
