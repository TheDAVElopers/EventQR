package com.thedavelopers.eventqr.features.attendee

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.EventRequestStatus
import com.thedavelopers.eventqr.core.util.DateFormatters
import com.thedavelopers.eventqr.features.events.model.dto.EventRequestResponse
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MyEventRequestsActivity : AppCompatActivity() {
    private lateinit var repository: AttendeeRepository
    private lateinit var listContainer: LinearLayout

    private val bg = Color.parseColor("#F7F7FA")
    private val textColor = Color.parseColor("#111827")
    private val muted = Color.parseColor("#6B7280")
    private val border = Color.parseColor("#E5E7EB")
    private val warning = Color.parseColor("#F97316")
    private val success = Color.parseColor("#009688")
    private val error = Color.parseColor("#EF4444")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = AttendeeRepository(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        setContentView(root)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(16))
            setBackgroundColor(Color.WHITE)
        }
        header.addView(label("‹  My Event Requests", 24, true).apply {
            setOnClickListener { finish() }
        })
        header.addView(label("Track submitted event creation requests.", 14, false, muted))
        root.addView(header)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(24))
        }
        scroll.addView(listContainer)
        root.addView(scroll)

        loadRequests()
    }

    private fun loadRequests() {
        listContainer.removeAllViews()
        listContainer.addView(messageCard("Loading your event requests..."))
        lifecycleScope.launch {
            when (val result = repository.getMyEventRequests()) {
                is NetworkResult.Success -> renderRequests(result.data)
                is NetworkResult.Error -> {
                    listContainer.removeAllViews()
                    listContainer.addView(messageCard(result.message))
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun renderRequests(requests: List<EventRequestResponse>) {
        listContainer.removeAllViews()
        if (requests.isEmpty()) {
            listContainer.addView(messageCard("No event requests yet."))
            return
        }
        requests.forEach { listContainer.addView(requestCard(it)) }
    }

    private fun requestCard(request: EventRequestResponse): LinearLayout =
        card().apply {
            val row = LinearLayout(this@MyEventRequestsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val titleBox = LinearLayout(this@MyEventRequestsActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            titleBox.addView(label(request.eventName, 18, true))
            titleBox.addView(label(request.eventCategory ?: "Uncategorized", 13, false, muted))
            row.addView(titleBox)
            row.addView(statusBadge(request.status))
            addView(row)
            addView(label("Venue: ${request.venue ?: "-"}", 14, false).withTop(12))
            addView(label("Schedule: ${DateFormatters.formatInstant(request.startDateTime)} - ${DateFormatters.formatInstant(request.endDateTime)}", 14, false, muted))
            addView(label("Capacity: ${request.capacity}", 14, false, muted))
            addView(label("Submitted: ${DateFormatters.formatInstant(request.createdAt)}", 13, false, muted).withTop(8))
            request.adminRemarks?.takeIf { it.isNotBlank() }?.let {
                addView(label("Admin remarks: $it", 13, false, muted).withTop(8))
            }
        }

    private fun messageCard(message: String): LinearLayout =
        card().apply {
            gravity = Gravity.CENTER
            addView(label(message, 15, false, muted).apply { gravity = Gravity.CENTER })
        }

    private fun card(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = rounded(Color.WHITE, 12, border)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, dp(12))
            }
        }

    private fun statusBadge(status: EventRequestStatus): TextView {
        val color = when (status) {
            EventRequestStatus.PENDING -> warning
            EventRequestStatus.APPROVED -> success
            EventRequestStatus.REJECTED -> error
        }
        return label(status.name.lowercase().replaceFirstChar { it.uppercase() }, 12, true, color).apply {
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = rounded(Color.TRANSPARENT, 16, color)
        }
    }

    private fun label(value: String, size: Int, bold: Boolean, color: Int = textColor): TextView =
        TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun TextView.withTop(top: Int): TextView = apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, dp(top), 0, 0)
        }
    }

    private fun rounded(color: Int, radiusDp: Int, strokeColor: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            strokeColor?.let { setStroke(dp(1), it) }
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
}
