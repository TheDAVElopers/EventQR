package com.thedavelopers.eventqr.ui.components

import android.content.Context
import android.view.ViewGroup
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import com.thedavelopers.eventqr.core.util.EventCardPresenter
import com.thedavelopers.eventqr.ui.theme.EventQrRowTheme
import com.thedavelopers.eventqr.ui.theme.LocalSpacing

data class EventCardState(
    val title: String = "",
    val status: String = "",
    val day: String = EventCardPresenter.UNKNOWN_DAY,
    val month: String = EventCardPresenter.UNKNOWN_MONTH,
    val time: String = EventCardPresenter.UNKNOWN_TIME,
    val location: String = "",
    val registeredCount: Int = 0,
    val capacity: Int = 1,
    val onClick: (() -> Unit)? = null,
)

class EventCardHolder(context: Context) {

    val view = ComposeView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    private val state = mutableStateOf(EventCardState())

    init {
        view.setContent {
            EventQrRowTheme {
                EventCardHost(state.value)
            }
        }
    }

    fun update(
        title: String,
        status: String,
        day: String,
        month: String,
        time: String,
        location: String,
        count: Int,
        capacity: Int,
        onClick: () -> Unit,
    ) {
        state.value = EventCardState(
            title = title,
            status = status,
            day = day,
            month = month,
            time = time,
            location = location,
            registeredCount = count,
            capacity = capacity,
            onClick = onClick,
        )
    }
}

@Composable
fun EventCardHost(state: EventCardState) {
    val spacing = LocalSpacing.current
    EventCard(
        title = state.title,
        status = parseBadgeStatus(state.status),
        day = state.day,
        month = state.month,
        time = state.time,
        location = state.location,
        registeredCount = state.registeredCount,
        capacity = state.capacity.coerceAtLeast(1),
        onClick = state.onClick,
        modifier = Modifier.padding(bottom = spacing.mediumSmall),
    )
}
