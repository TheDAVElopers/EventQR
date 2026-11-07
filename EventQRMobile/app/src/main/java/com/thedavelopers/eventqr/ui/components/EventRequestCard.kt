package com.thedavelopers.eventqr.ui.components

import android.content.Context
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.util.DateFormatters
import com.thedavelopers.eventqr.features.events.eventRequestBadgeStatus
import com.thedavelopers.eventqr.features.events.model.dto.EventRequestResponse
import com.thedavelopers.eventqr.features.events.requesterDisplay
import com.thedavelopers.eventqr.ui.theme.EventQrRowTheme
import com.thedavelopers.eventqr.ui.theme.LocalGrid
import com.thedavelopers.eventqr.ui.theme.LocalType
import com.thedavelopers.eventqr.ui.theme.UiHairline
import com.thedavelopers.eventqr.ui.theme.UiSurface
import com.thedavelopers.eventqr.ui.theme.UiTextPrimary
import com.thedavelopers.eventqr.ui.theme.UiTextSecondary

/*
 * Event request card. Same public API as before.
 *
 * A request is a row, not a decorated card: hairline card, request name in title, the ONE status
 * badge (Pending review / Approved / Rejected - §5.4 mapping), submitted meta in body_s, and the
 * quiet 4dp gap rhythm of the new list language. No accent stripe, no shadow.
 */

class EventRequestHolder(
    context: Context,
    private val showRequester: Boolean = false,
) {

    val view = ComposeView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    var onClick: (EventRequestResponse) -> Unit = {}

    private val state = mutableStateOf<EventRequestResponse?>(null)

    init {
        view.setContent {
            EventQrRowTheme {
                state.value?.let { request ->
                    EventRequestCard(
                        request = request,
                        onClick = { onClick(request) },
                        showRequester = showRequester,
                    )
                }
            }
        }
    }

    fun update(request: EventRequestResponse) {
        state.value = request
    }
}

/**
 * @param showRequester the admin queue leads with the person who asked: requester as title, event
 *   and submitted date in the meta. The attendee's own request list keeps the event name, because
 *   every row there is the same requester.
 */
@Composable
fun EventRequestCard(
    request: EventRequestResponse,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showRequester: Boolean = false,
) {
    val grid = LocalGrid.current
    val type = LocalType.current
    val shape = RoundedCornerShape(grid.radiusCard)
    val eventName = request.eventName.ifBlank { stringResource(R.string.common_untitled_event) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = grid.s8)
            .clip(shape)
            .background(UiSurface)
            .border(dimensionResource(R.dimen.stroke_hairline), UiHairline, shape)
            .clickable(onClick = onClick)
            .padding(grid.cardPadding),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (showRequester) {
                    requesterDisplay(request.requesterName, request.contactEmail, request.requesterUserId)
                } else {
                    eventName
                },
                style = type.title,
                color = UiTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(grid.s8))
            StatusBadge(status = eventRequestBadgeStatus(request.status), showIcon = false)
        }

        Spacer(modifier = Modifier.height(grid.s4))

        Text(
            text = if (showRequester) {
                stringResource(
                    R.string.ui_admin_pending_request_meta,
                    eventName,
                    DateFormatters.formatEventDate(request.createdAt),
                )
            } else {
                stringResource(
                    R.string.event_request_card_submitted_submitted_1_s,
                    DateFormatters.formatEventDate(request.createdAt),
                )
            },
            style = type.bodyS,
            color = UiTextSecondary,
        )
    }
}
