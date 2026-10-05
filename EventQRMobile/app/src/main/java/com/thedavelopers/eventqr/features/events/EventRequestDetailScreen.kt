package com.thedavelopers.eventqr.features.events

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thedavelopers.eventqr.core.api.dto.EventRequestStatus
import com.thedavelopers.eventqr.features.events.model.dto.EventRequestResponse
import com.thedavelopers.eventqr.ui.components.EmptyStateView
import com.thedavelopers.eventqr.ui.components.EventBadgeStatus
import com.thedavelopers.eventqr.ui.components.EventQrTopAppBar
import com.thedavelopers.eventqr.ui.components.StatusBadge
import com.thedavelopers.eventqr.ui.theme.BackgroundLight
import com.thedavelopers.eventqr.ui.theme.BorderLight
import com.thedavelopers.eventqr.ui.theme.BrandPrimary
import com.thedavelopers.eventqr.ui.theme.PaperWhite
import com.thedavelopers.eventqr.ui.theme.StatusApprovedGreen
import com.thedavelopers.eventqr.ui.theme.StatusApprovedGreenBg
import com.thedavelopers.eventqr.ui.theme.StatusApprovedGreenText
import com.thedavelopers.eventqr.ui.theme.StatusRejectedRed
import com.thedavelopers.eventqr.ui.theme.StatusRejectedRedBg
import com.thedavelopers.eventqr.ui.theme.StatusRejectedRedText
import com.thedavelopers.eventqr.ui.theme.TextMuted
import com.thedavelopers.eventqr.ui.theme.TextOnPrimary
import com.thedavelopers.eventqr.ui.theme.TextPrimary
import com.thedavelopers.eventqr.ui.theme.TextSecondary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun EventRequestDetailScreen(
    request: EventRequestResponse?,
    isLoading: Boolean,
    errorMessage: String?,
    isAdmin: Boolean,
    onBackClick: () -> Unit,
    onRetryClick: () -> Unit,
    onApproveClick: ((remarks: String?) -> Unit)? = null,
    onRejectClick: ((remarks: String?) -> Unit)? = null,
    onUpgradeClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var showConfirmDialog by remember { mutableStateOf<ConfirmAction?>(null) }
    var remarksText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            EventQrTopAppBar(
                title = "Event Request",
                onBackClick = onBackClick,
            )
        },
        containerColor = BackgroundLight,
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                isLoading && request == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = BrandPrimary)
                    }
                }
                errorMessage != null && request == null -> {
                    EmptyStateView(
                        icon = Icons.Default.Error,
                        title = "Unable to Load Request",
                        description = errorMessage,
                        actionLabel = "Retry",
                        onActionClick = onRetryClick,
                        iconTint = StatusRejectedRedText,
                        iconBackgroundColor = StatusRejectedRedBg,
                    )
                }
                request != null -> {
                    val scrollState = rememberScrollState()
                    val formatter = remember {
                        DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.of("Asia/Manila"))
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(16.dp),
                    ) {
                        // Title & Status Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = PaperWhite),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = request.eventName.ifBlank { "Untitled Event" },
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                        color = TextPrimary,
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

                                if (isAdmin) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val requester = request.requesterName?.takeIf { it.isNotBlank() }
                                        ?: request.contactEmail?.takeIf { it.isNotBlank() }
                                        ?: request.requesterUserId.toString()
                                    Text(
                                        text = "Submitted by: $requester",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Submitted on ${formatDate(request.createdAt, formatter)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Admin Remarks Note (if approved or rejected)
                        if (request.status == EventRequestStatus.APPROVED) {
                            NoteBox(
                                title = "Approval Note",
                                message = request.adminRemarks?.takeIf { it.isNotBlank() }
                                    ?: "Approved. Venue confirmed. Please proceed to event setup.",
                                backgroundColor = StatusApprovedGreenBg,
                                textColor = StatusApprovedGreenText,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        } else if (request.status == EventRequestStatus.REJECTED) {
                            NoteBox(
                                title = "Rejection Note",
                                message = request.adminRemarks?.takeIf { it.isNotBlank() }
                                    ?: "This request was rejected. Review the event details and submit a revised request if needed.",
                                backgroundColor = StatusRejectedRedBg,
                                textColor = StatusRejectedRedText,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Event Information Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = PaperWhite),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Text(
                                    text = "Description",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = TextMuted,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = request.eventDescription?.takeIf { it.isNotBlank() }
                                        ?: "No description provided.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary,
                                )

                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 14.dp),
                                    color = BorderLight,
                                )

                                InfoRow(
                                    icon = Icons.Default.CalendarToday,
                                    label = "PROPOSED DATE",
                                    value = formatDate(request.startDateTime, formatter),
                                )

                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    color = BorderLight,
                                )

                                InfoRow(
                                    icon = Icons.Default.LocationOn,
                                    label = "LOCATION / VENUE",
                                    value = request.venue?.takeIf { it.isNotBlank() } ?: "Not available",
                                )

                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    color = BorderLight,
                                )

                                InfoRow(
                                    icon = Icons.Default.Groups,
                                    label = "EXPECTED ATTENDEES",
                                    value = request.capacity.toString(),
                                )

                                if (isAdmin && !request.contactEmail.isNullOrBlank()) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 12.dp),
                                        color = BorderLight,
                                    )
                                    InfoRow(
                                        icon = Icons.Default.Person,
                                        label = "CONTACT EMAIL",
                                        value = request.contactEmail,
                                    )
                                }
                            }
                        }

                        // Admin Action Buttons
                        if (isAdmin) {
                            Spacer(modifier = Modifier.height(24.dp))

                            if (request.status == EventRequestStatus.PENDING) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            remarksText = ""
                                            showConfirmDialog = ConfirmAction.REJECT
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = StatusRejectedRed,
                                        ),
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Reject", fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = {
                                            remarksText = ""
                                            showConfirmDialog = ConfirmAction.APPROVE
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = StatusApprovedGreen,
                                            contentColor = TextOnPrimary,
                                        ),
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Approve", fontWeight = FontWeight.Bold)
                                    }
                                }
                            } else if (request.status == EventRequestStatus.APPROVED) {
                                Button(
                                    onClick = {
                                        if (!request.organizerUpgraded && onUpgradeClick != null) {
                                            showConfirmDialog = ConfirmAction.UPGRADE
                                        }
                                    },
                                    enabled = !request.organizerUpgraded,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = BrandPrimary,
                                        contentColor = TextOnPrimary,
                                        disabledContainerColor = BorderLight,
                                        disabledContentColor = TextMuted,
                                    ),
                                ) {
                                    Text(
                                        text = if (request.organizerUpgraded) "Upgraded to Organizer" else "Upgrade to Organizer",
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    // Confirmation Dialog
    showConfirmDialog?.let { action ->
        AlertDialog(
            onDismissRequest = { showConfirmDialog = null },
            title = {
                Text(
                    text = when (action) {
                        ConfirmAction.APPROVE -> "Approve Request?"
                        ConfirmAction.REJECT -> "Reject Request?"
                        ConfirmAction.UPGRADE -> "Upgrade to Organizer?"
                    },
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column {
                    Text(
                        text = when (action) {
                            ConfirmAction.APPROVE -> "Approve this event creation request? The requester will be notified."
                            ConfirmAction.REJECT -> "Reject this event creation request? The requester will be notified."
                            ConfirmAction.UPGRADE -> "This will upgrade the requester's account to Organizer role."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (action == ConfirmAction.APPROVE || action == ConfirmAction.REJECT) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = remarksText,
                            onValueChange = { remarksText = it },
                            label = { Text("Remarks (Optional)") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val act = showConfirmDialog
                        showConfirmDialog = null
                        when (act) {
                            ConfirmAction.APPROVE -> onApproveClick?.invoke(remarksText.ifBlank { null })
                            ConfirmAction.REJECT -> onRejectClick?.invoke(remarksText.ifBlank { null })
                            ConfirmAction.UPGRADE -> onUpgradeClick?.invoke()
                            null -> Unit
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (action == ConfirmAction.REJECT) StatusRejectedRed else BrandPrimary,
                    ),
                ) {
                    Text(
                        text = when (action) {
                            ConfirmAction.APPROVE -> "Approve"
                            ConfirmAction.REJECT -> "Reject"
                            ConfirmAction.UPGRADE -> "Upgrade"
                        },
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

private enum class ConfirmAction {
    APPROVE,
    REJECT,
    UPGRADE,
}

@Composable
private fun NoteBox(
    title: String,
    message: String,
    backgroundColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .padding(16.dp),
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = textColor,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
            )
        }
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = BrandPrimary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = TextMuted,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
            )
        }
    }
}

private fun formatDate(value: Instant?, formatter: DateTimeFormatter): String {
    return value?.let { formatter.format(it) } ?: "Not available"
}
