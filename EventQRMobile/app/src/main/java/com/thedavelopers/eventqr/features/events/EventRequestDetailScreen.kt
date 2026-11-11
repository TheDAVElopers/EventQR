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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.EventRequestStatus
import com.thedavelopers.eventqr.core.util.DateFormatters
import com.thedavelopers.eventqr.features.events.model.dto.EventRequestResponse
import com.thedavelopers.eventqr.ui.components.EmptyState
import com.thedavelopers.eventqr.ui.components.EventBadgeStatus
import com.thedavelopers.eventqr.ui.components.StatusBadge
import com.thedavelopers.eventqr.ui.components.UiButton
import com.thedavelopers.eventqr.ui.components.UiButtonTone
import com.thedavelopers.eventqr.ui.icons.AppIcons
import com.thedavelopers.eventqr.ui.theme.LocalGrid
import com.thedavelopers.eventqr.ui.theme.LocalType
import com.thedavelopers.eventqr.ui.theme.UiError
import com.thedavelopers.eventqr.ui.theme.UiHairline
import com.thedavelopers.eventqr.ui.theme.UiPrimary
import com.thedavelopers.eventqr.ui.theme.UiSurface
import com.thedavelopers.eventqr.ui.theme.UiSurfaceSunken
import com.thedavelopers.eventqr.ui.theme.UiTextPrimary
import com.thedavelopers.eventqr.ui.theme.UiTextSecondary
import java.util.UUID

private enum class ConfirmAction {
    APPROVE,
    REJECT,
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

/**
 * Event request detail (§6.5), shared by admin and attendee.
 *
 * The host activity owns the top bar (§5.8) through `setContentWithDetailHeader`, so this screen
 * draws no header and no Scaffold. The request is a printed spec table: `overline` label left,
 * value right, 1dp hairlines, no icon rows, no nested cards, no elevation. Actions use the shared
 * `UiButton` family - approve is primary, reject is the outlined destructive trigger (§5.1).
 */
@Composable
fun EventRequestDetailScreen(
    request: EventRequestResponse?,
    isLoading: Boolean,
    errorMessage: String?,
    isAdmin: Boolean,
    onRetryClick: () -> Unit,
    onApproveClick: ((remarks: String?) -> Unit)? = null,
    onRejectClick: ((remarks: String?) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val grid = LocalGrid.current
    val type = LocalType.current
    var showConfirmDialog by remember { mutableStateOf<ConfirmAction?>(null) }
    var remarksText by rememberSaveable { mutableStateOf("") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(com.thedavelopers.eventqr.ui.theme.UiBackground),
    ) {
        when {
            isLoading && request == null -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = UiPrimary, strokeWidth = grid.s2)
            }

            errorMessage != null && request == null -> EmptyState(
                icon = AppIcons.Error,
                title = stringResource(R.string.ui_request_detail_error_title),
                description = errorMessage,
                actionLabel = stringResource(R.string.common_retry),
                onActionClick = onRetryClick,
                primaryAction = true,
            )

            request != null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = grid.gutter),
            ) {
                // ── Identity: name, status badge, who and when ──
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = grid.s20),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = request.eventName.ifBlank { stringResource(R.string.common_untitled_event) },
                        style = type.titleL,
                        color = UiTextPrimary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.size(grid.s8))
                    StatusBadge(status = eventRequestBadgeStatus(request.status), showIcon = false)
                }

                if (isAdmin) {
                    Text(
                        text = stringResource(
                            R.string.ui_request_detail_submitted_by,
                            requesterDisplay(request.requesterName, request.contactEmail, request.requesterUserId),
                        ),
                        style = type.bodyS,
                        color = UiTextSecondary,
                        modifier = Modifier.padding(top = grid.s8),
                    )
                }

                Text(
                    text = stringResource(
                        R.string.ui_request_detail_submitted_on,
                        DateFormatters.formatEventDate(request.createdAt),
                    ),
                    style = type.micro,
                    color = UiTextSecondary,
                    modifier = Modifier.padding(top = grid.s4),
                )

                if (request.status == EventRequestStatus.APPROVED || request.status == EventRequestStatus.REJECTED) {
                    val approval = request.status == EventRequestStatus.APPROVED
                    NoteBlock(
                        title = stringResource(
                            if (approval) R.string.ui_request_detail_approval_note else R.string.ui_request_detail_rejection_note,
                        ),
                        message = request.adminRemarks?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.ui_request_detail_no_note),
                        tone = if (approval) NoteTone.INFO else NoteTone.ERROR,
                        modifier = Modifier.padding(top = grid.s16),
                    )
                }

                // ── Description ──
                SectionEyebrow(stringResource(R.string.ui_request_detail_description))
                Text(
                    text = request.eventDescription?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.ui_request_detail_no_description),
                    style = type.body,
                    color = UiTextPrimary,
                )

                // ── The spec table: every row is label + value + hairline ──
                SectionEyebrow(stringResource(R.string.ui_request_detail_duration))
                SpecRow(
                    label = stringResource(R.string.ui_request_detail_duration),
                    value = listOf(request.startDateTime, request.endDateTime)
                        .joinToString(" – ") { it?.let(DateFormatters::formatEventDateTime) ?: "—" },
                    showLabel = false,
                )
                if (request.registrationStartDateTime != null || request.registrationEndDateTime != null) {
                    SpecRow(
                        label = stringResource(R.string.ui_request_detail_registration),
                        value = listOf(request.registrationStartDateTime, request.registrationEndDateTime)
                            .joinToString(" – ") { it?.let(DateFormatters::formatEventDateTime) ?: "—" },
                    )
                }
                request.eventCategory?.takeIf { it.isNotBlank() }?.let { category ->
                    SpecRow(label = stringResource(R.string.ui_request_detail_category), value = category)
                }
                request.targetAudience?.takeIf { it.isNotBlank() }?.let { audience ->
                    SpecRow(label = stringResource(R.string.ui_request_detail_audience), value = audience)
                }
                SpecRow(
                    label = stringResource(R.string.ui_request_detail_venue),
                    value = request.venue?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.ui_request_detail_not_available),
                )
                SpecRow(
                    label = stringResource(R.string.ui_request_detail_capacity),
                    value = request.capacity.toString(),
                )
                if (isAdmin && !request.contactEmail.isNullOrBlank()) {
                    SpecRow(label = stringResource(R.string.ui_request_detail_contact_email), value = request.contactEmail)
                }
                if (isAdmin && !request.contactNumber.isNullOrBlank()) {
                    SpecRow(label = stringResource(R.string.ui_request_detail_contact_phone), value = request.contactNumber)
                }
                if (!request.reasonForRequest.isNullOrBlank()) {
                    SectionEyebrow(stringResource(R.string.ui_request_detail_reason))
                    Text(text = request.reasonForRequest, style = type.body, color = UiTextPrimary)
                }

                // ── Actions ──
                if (isAdmin) {
                    Spacer(modifier = Modifier.height(grid.s24))
                    if (request.status == EventRequestStatus.PENDING) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(grid.s12),
                        ) {
                            UiButton(
                                text = stringResource(R.string.ui_request_detail_reject),
                                onClick = {
                                    remarksText = ""
                                    showConfirmDialog = ConfirmAction.REJECT
                                },
                                modifier = Modifier.weight(1f),
                                tone = UiButtonTone.DESTRUCTIVE,
                                inline = true,
                            )
                            UiButton(
                                text = stringResource(R.string.ui_request_detail_approve),
                                onClick = {
                                    remarksText = ""
                                    showConfirmDialog = ConfirmAction.APPROVE
                                },
                                modifier = Modifier.weight(1f),
                                inline = true,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(grid.s32))
            }
        }
    }

    showConfirmDialog?.let { action ->
        ConfirmDialog(
            action = action,
            remarks = remarksText,
            onRemarksChange = { remarksText = it },
            onDismiss = { showConfirmDialog = null },
            onConfirm = {
                val act = showConfirmDialog
                showConfirmDialog = null
                when (act) {
                    ConfirmAction.APPROVE -> onApproveClick?.invoke(remarksText.ifBlank { null })
                    ConfirmAction.REJECT -> onRejectClick?.invoke(remarksText.ifBlank { null })
                    null -> Unit
                }
            },
        )
    }
}

@Composable
private fun SectionEyebrow(text: String) {
    val grid = LocalGrid.current
    val type = LocalType.current
    Text(
        text = text,
        style = type.overline,
        color = UiTextSecondary,
        modifier = Modifier.padding(top = grid.s24, bottom = grid.s8),
    )
}

/** One spec-table row (§6.5 / §5.9 form 2): label left, value right, hairline underneath. */
@Composable
private fun SpecRow(label: String, value: String, showLabel: Boolean = true) {
    val grid = LocalGrid.current
    val type = LocalType.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(grid.metricRowHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showLabel) {
                Text(
                    text = label,
                    style = type.overline,
                    color = UiTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            Text(
                text = value,
                style = type.numericS,
                color = UiTextPrimary,
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = grid.s16),
            )
        }
        Box(modifier = Modifier.fillMaxWidth().height(androidx.compose.ui.res.dimensionResource(R.dimen.stroke_hairline)).background(UiHairline))
    }
}

private enum class NoteTone { INFO, ERROR }

/**
 * A note is a flat semantic block, not a card: tonal fill, no border, no elevation. Approval is
 * informational; rejection is the error tone. Lime never appears here (nothing is confirmed).
 */
@Composable
private fun NoteBlock(
    title: String,
    message: String,
    tone: NoteTone,
    modifier: Modifier = Modifier,
) {
    val grid = LocalGrid.current
    val type = LocalType.current
    val fill: Color
    val ink: Color
    when (tone) {
        NoteTone.INFO -> {
            fill = com.thedavelopers.eventqr.ui.theme.UiInfoContainer
            ink = com.thedavelopers.eventqr.ui.theme.UiOnInfoContainer
        }
        NoteTone.ERROR -> {
            fill = com.thedavelopers.eventqr.ui.theme.UiErrorContainer
            ink = com.thedavelopers.eventqr.ui.theme.UiOnErrorContainer
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(grid.radiusCard))
            .background(fill)
            .padding(grid.s16),
    ) {
        Text(text = title, style = type.overline, color = ink)
        Spacer(modifier = Modifier.height(grid.s4))
        Text(text = message, style = type.bodyS, color = ink)
    }
}

/**
 * Confirm dialog (§5.12): radius 16, title 18/600, body 15 secondary, text actions, destructive
 * commit filled in the error tone with the verb as its label.
 */
@Composable
private fun ConfirmDialog(
    action: ConfirmAction,
    remarks: String,
    onRemarksChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val grid = LocalGrid.current
    val type = LocalType.current
    val title = stringResource(
        when (action) {
            ConfirmAction.APPROVE -> R.string.ui_request_detail_confirm_approve
            ConfirmAction.REJECT -> R.string.ui_request_detail_confirm_reject
        },
    )
    val body = stringResource(
        when (action) {
            ConfirmAction.APPROVE -> R.string.ui_request_detail_confirm_approve_body
            ConfirmAction.REJECT -> R.string.ui_request_detail_confirm_reject_body
        },
    )
    val confirmsRemarks = action == ConfirmAction.APPROVE || action == ConfirmAction.REJECT
    val commitColor = if (action == ConfirmAction.REJECT) UiError else UiPrimary

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(grid.radiusSheet),
        containerColor = UiSurface,
        title = { Text(text = title, style = type.title, color = UiTextPrimary, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text(text = body, style = type.body, color = UiTextSecondary)
                if (confirmsRemarks) {
                    Spacer(modifier = Modifier.height(grid.s12))
                    androidx.compose.material3.OutlinedTextField(
                        value = remarks,
                        onValueChange = onRemarksChange,
                        singleLine = false,
                        maxLines = 3,
                        shape = RoundedCornerShape(grid.radiusButton),
                        placeholder = {
                            Text(
                                text = stringResource(R.string.ui_request_detail_remarks),
                                style = type.bodyS,
                                color = UiTextSecondary,
                            )
                        },
                        textStyle = type.body,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = UiSurfaceSunken,
                            unfocusedContainerColor = UiSurfaceSunken,
                            focusedBorderColor = UiPrimary,
                            unfocusedBorderColor = com.thedavelopers.eventqr.ui.theme.UiOutlineInteractive,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = title.trimEnd('?'),
                    style = type.label,
                    color = commitColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.request_event_cancel), style = type.label, color = UiPrimary)
            }
        },
    )
}

