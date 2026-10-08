package com.thedavelopers.eventqr.features.events

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import com.thedavelopers.eventqr.core.api.dto.EventRequestStatus
import com.thedavelopers.eventqr.core.util.DateFormatters
import com.thedavelopers.eventqr.features.events.model.dto.EventRequestResponse
import com.thedavelopers.eventqr.ui.components.EmptyStateView
import com.thedavelopers.eventqr.ui.components.EventBadgeStatus
import com.thedavelopers.eventqr.ui.components.StatusBadge
import com.thedavelopers.eventqr.ui.theme.LocalSpacing
import java.util.UUID
import androidx.compose.ui.res.stringResource
import com.thedavelopers.eventqr.R

private enum class ConfirmAction {
    APPROVE,
    REJECT,
    UPGRADE,
}

internal fun eventRequestBadgeStatus(status: EventRequestStatus): EventBadgeStatus = when (status) {
    EventRequestStatus.APPROVED -> EventBadgeStatus.APPROVED
    EventRequestStatus.REJECTED -> EventBadgeStatus.REJECTED
    EventRequestStatus.PENDING -> EventBadgeStatus.PENDING
}

internal fun requesterDisplay(
    requesterName: String?,
    contactEmail: String?,
    requesterUserId: UUID?,
): String {
    requesterName?.takeIf { it.isNotBlank() }?.let { return it }
    contactEmail?.takeIf { it.isNotBlank() }?.let { return it }
    return "Unknown requester"
}

@Composable
fun EventRequestDetailScreen(
    request: EventRequestResponse?,
    isLoading: Boolean,
    errorMessage: String?,
    isAdmin: Boolean,
    onRetryClick: () -> Unit,
    onApproveClick: ((remarks: String?) -> Unit)? = null,
    onRejectClick: ((remarks: String?) -> Unit)? = null,
    onUpgradeClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val colorScheme = MaterialTheme.colorScheme
    var showConfirmDialog by remember { mutableStateOf<ConfirmAction?>(null) }
    var remarksText by rememberSaveable { mutableStateOf("") }

    Scaffold(
        contentWindowInsets = WindowInsets.navigationBars,
        containerColor = colorScheme.background,
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
                        CircularProgressIndicator(color = colorScheme.primary)
                    }
                }
                errorMessage != null && request == null -> {
                    EmptyStateView(
                        icon = Icons.Default.Error,
                        title = "Unable to Load Request",
                        description = errorMessage,
                        actionLabel = "Retry",
                        onActionClick = onRetryClick,
                        iconTint = colorScheme.onErrorContainer,
                        iconBackgroundColor = colorScheme.errorContainer,
                    )
                }
                request != null -> {
                    val scrollState = rememberScrollState()

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(spacing.screenHorizontalPadding),
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(spacing.cardCornerRadius),
                            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = spacing.cardElevation,
                            ),
                        ) {
                            Column(modifier = Modifier.padding(spacing.mediumLarge)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = request.eventName.ifBlank { stringResource(R.string.common_untitled_event) },
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = colorScheme.onSurface,
                                        ),
                                        modifier = Modifier.weight(1f),
                                    )
                                    Spacer(modifier = Modifier.width(spacing.small))
                                    StatusBadge(status = eventRequestBadgeStatus(request.status))
                                }

                                if (isAdmin) {
                                    Spacer(modifier = Modifier.height(spacing.small))
                                    Text(
                                        text = stringResource(R.string.event_request_detail_submitted_by_1_s, requesterDisplay(request.requesterName, request.contactEmail, request.requesterUserId)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant,
                                    )
                                }

                                Spacer(modifier = Modifier.height(spacing.extraSmall))
                                Text(
                                    text = stringResource(R.string.event_request_detail_submitted_on_1_s, DateFormatters.formatEventDate(request.createdAt)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(spacing.medium))

                        if (request.status == EventRequestStatus.APPROVED) {
                            NoteBox(
                                title = "Approval Note",
                                message = request.adminRemarks?.takeIf { it.isNotBlank() }
                                    ?: "No note provided.",
                                backgroundColor = colorScheme.primaryContainer,
                                textColor = colorScheme.onPrimaryContainer,
                            )
                            Spacer(modifier = Modifier.height(spacing.medium))
                        } else if (request.status == EventRequestStatus.REJECTED) {
                            NoteBox(
                                title = "Rejection Note",
                                message = request.adminRemarks?.takeIf { it.isNotBlank() }
                                    ?: "No note provided.",
                                backgroundColor = colorScheme.errorContainer,
                                textColor = colorScheme.onErrorContainer,
                            )
                            Spacer(modifier = Modifier.height(spacing.medium))
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(spacing.cardCornerRadius),
                            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = spacing.cardElevation,
                            ),
                        ) {
                            Column(modifier = Modifier.padding(spacing.mediumLarge)) {
                                Text(
                                    text = stringResource(R.string.common_description),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.onSurfaceVariant,
                                    ),
                                )
                                Spacer(modifier = Modifier.height(spacing.extraSmall))
                                Text(
                                    text = request.eventDescription?.takeIf { it.isNotBlank() }
                                        ?: "No description provided.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colorScheme.onSurface,
                                )

                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = spacing.cardContentGap),
                                    color = colorScheme.outlineVariant,
                                )

                                InfoRow(
                                    icon = Icons.Default.CalendarToday,
                                    label = "EVENT DURATION",
                                    value = listOf(request.startDateTime, request.endDateTime)
                                        .joinToString(" - ") { it?.let(DateFormatters::formatEventDate) ?: "—" },
                                )

                                if (request.registrationStartDateTime != null || request.registrationEndDateTime != null) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = spacing.mediumSmall),
                                        color = colorScheme.outlineVariant,
                                    )
                                    InfoRow(
                                        icon = Icons.Default.CalendarToday,
                                        label = "REGISTRATION WINDOW",
                                        value = listOf(request.registrationStartDateTime, request.registrationEndDateTime)
                                            .joinToString(" - ") { it?.let(DateFormatters::formatEventDate) ?: "—" },
                                    )
                                }

                                request.eventCategory?.takeIf { it.isNotBlank() }?.let { category ->
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = spacing.mediumSmall),
                                        color = colorScheme.outlineVariant,
                                    )
                                    InfoRow(
                                        icon = Icons.Default.Category,
                                        label = "CATEGORY",
                                        value = category,
                                    )
                                }

                                request.targetAudience?.takeIf { it.isNotBlank() }?.let { audience ->
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = spacing.mediumSmall),
                                        color = colorScheme.outlineVariant,
                                    )
                                    InfoRow(
                                        icon = Icons.Default.Groups,
                                        label = "TARGET AUDIENCE",
                                        value = audience,
                                    )
                                }

                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = spacing.mediumSmall),
                                    color = colorScheme.outlineVariant,
                                )

                                InfoRow(
                                    icon = Icons.Default.LocationOn,
                                    label = "LOCATION / VENUE",
                                    value = request.venue?.takeIf { it.isNotBlank() } ?: "Not available",
                                )

                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = spacing.mediumSmall),
                                    color = colorScheme.outlineVariant,
                                )

                                InfoRow(
                                    icon = Icons.Default.Groups,
                                    label = "EXPECTED ATTENDEES",
                                    value = request.capacity.toString(),
                                )

                                if (isAdmin && !request.contactEmail.isNullOrBlank()) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = spacing.mediumSmall),
                                        color = colorScheme.outlineVariant,
                                    )
                                    InfoRow(
                                        icon = Icons.Default.Person,
                                        label = "CONTACT EMAIL",
                                        value = request.contactEmail,
                                    )
                                }

                                if (isAdmin && !request.contactNumber.isNullOrBlank()) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = spacing.mediumSmall),
                                        color = colorScheme.outlineVariant,
                                    )
                                    InfoRow(
                                        icon = Icons.Default.Phone,
                                        label = "CONTACT NUMBER",
                                        value = request.contactNumber,
                                    )
                                }

                                if (!request.reasonForRequest.isNullOrBlank()) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = spacing.cardContentGap),
                                        color = colorScheme.outlineVariant,
                                    )
                                    Text(
                                        text = "REASON FOR REQUEST",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = colorScheme.onSurfaceVariant,
                                        ),
                                    )
                                    Spacer(modifier = Modifier.height(spacing.extraSmall))
                                    Text(
                                        text = request.reasonForRequest,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colorScheme.onSurface,
                                    )
                                }
                            }
                        }

                        if (isAdmin) {
                            Spacer(modifier = Modifier.height(spacing.large))

                            if (request.status == EventRequestStatus.PENDING) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(spacing.mediumSmall),
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            remarksText = ""
                                            showConfirmDialog = ConfirmAction.REJECT
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = spacing.buttonHeightMin),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = colorScheme.error,
                                        ),
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = null,
                                            modifier = Modifier.size(spacing.iconSizeMedium),
                                        )
                                        Spacer(modifier = Modifier.width(spacing.micro))
                                        Text(stringResource(R.string.common_reject), fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = {
                                            remarksText = ""
                                            showConfirmDialog = ConfirmAction.APPROVE
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = spacing.buttonHeightMin),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = colorScheme.tertiary,
                                            contentColor = colorScheme.onTertiary,
                                        ),
                                    ) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(spacing.iconSizeMedium),
                                        )
                                        Spacer(modifier = Modifier.width(spacing.micro))
                                        Text(stringResource(R.string.common_approve), fontWeight = FontWeight.Bold)
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
                                        .heightIn(min = spacing.buttonHeightMin),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colorScheme.primary,
                                        contentColor = colorScheme.onPrimary,
                                        disabledContainerColor = colorScheme.surfaceVariant,
                                        disabledContentColor = colorScheme.onSurfaceVariant,
                                    ),
                                ) {
                                    Text(
                                        text = if (request.organizerUpgraded) {
                                            "Upgraded to Organizer"
                                        } else {
                                            "Upgrade to Organizer"
                                        },
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(spacing.large))
                    }
                }
            }
        }
    }

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
                            ConfirmAction.APPROVE ->
                                "Approve this event creation request? The requester will be notified."
                            ConfirmAction.REJECT ->
                                "Reject this event creation request? The requester will be notified."
                            ConfirmAction.UPGRADE ->
                                "This will upgrade the requester's account to Organizer role."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (action == ConfirmAction.APPROVE || action == ConfirmAction.REJECT) {
                        Spacer(modifier = Modifier.height(spacing.mediumSmall))
                        OutlinedTextField(
                            value = remarksText,
                            onValueChange = { remarksText = it },
                            label = { Text(stringResource(R.string.common_remarks_optional)) },
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
                        containerColor = if (action == ConfirmAction.REJECT) {
                            colorScheme.error
                        } else {
                            colorScheme.primary
                        },
                        contentColor = if (action == ConfirmAction.REJECT) {
                            colorScheme.onError
                        } else {
                            colorScheme.onPrimary
                        },
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
                    Text(stringResource(R.string.request_event_cancel))
                }
            },
        )
    }
}

@Composable
private fun NoteBox(
    title: String,
    message: String,
    backgroundColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(spacing.cardCornerRadiusSmall))
            .background(backgroundColor)
            .padding(spacing.medium),
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = textColor,
            )
            Spacer(modifier = Modifier.height(spacing.extraSmall))
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
    val spacing = LocalSpacing.current
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colorScheme.primary,
            modifier = Modifier.size(spacing.iconSizeMedium),
        )
        Spacer(modifier = Modifier.width(spacing.mediumSmall))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(spacing.nano))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colorScheme.onSurface,
            )
        }
    }
}
