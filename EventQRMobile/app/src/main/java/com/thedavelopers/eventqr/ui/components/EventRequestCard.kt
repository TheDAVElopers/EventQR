package com.thedavelopers.eventqr.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thedavelopers.eventqr.core.api.dto.EventRequestStatus
import com.thedavelopers.eventqr.features.events.model.dto.EventRequestResponse
import com.thedavelopers.eventqr.ui.theme.PaperWhite
import com.thedavelopers.eventqr.ui.theme.TextMuted
import com.thedavelopers.eventqr.ui.theme.TextPrimary
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun EventRequestCard(
    request: EventRequestResponse,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PaperWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = request.eventName.ifBlank { "Untitled Event" },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                val badgeStatus = when (request.status) {
                    EventRequestStatus.APPROVED -> EventBadgeStatus.APPROVED
                    EventRequestStatus.REJECTED -> EventBadgeStatus.REJECTED
                    EventRequestStatus.PENDING -> EventBadgeStatus.PENDING
                }
                StatusBadge(status = badgeStatus)
            }

            Spacer(modifier = Modifier.height(6.dp))

            val dateStr = request.createdAt?.let {
                DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.of("Asia/Manila")).format(it)
            } ?: "-"
            Text(
                text = "Submitted $dateStr",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }
    }
}
