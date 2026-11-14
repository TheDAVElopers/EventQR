package com.thedavelopers.eventqr.features.events

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
import com.thedavelopers.eventqr.ui.theme.UiErrorContainer
import com.thedavelopers.eventqr.ui.theme.UiHairline
import com.thedavelopers.eventqr.ui.theme.UiOnErrorContainer
import com.thedavelopers.eventqr.ui.theme.UiOnSuccessContainer
import com.thedavelopers.eventqr.ui.theme.UiPrimary
import com.thedavelopers.eventqr.ui.theme.UiSuccessContainer
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
    unknownLabel: String,
): String {
    requesterName?.takeIf { it.isNotBlank() }?.let { return it }
    contactEmail?.takeIf { it.isNotBlank() }?.let { return it }
    return unknownLabel
}

/**
 * Event request detail (§6.5), shared by admin and attendee.
 *
 * The host activity owns the top bar (§5.8) through `setContentWithDetailHeader`, so this screen
 * draws no header and no Scaffold. The request is a printed spec table: `overline` label left,
 * value right, 1dp hairlines, no icon rows, no nested cards, no elevation. For an admin on a
 * pending request the decision pair is a 72dp sticky footer outside the scroll - primary Approve
 * last, destructive-outline Reject (§5.1, §6.7).
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
    decisionMessage: String? = null,
    decisionIsError: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val grid = LocalGrid.current
    val type = LocalType.current
    var showConfirmDialog by remember { mutableStateOf<ConfirmAction?>(null) }
    var remarksText by rememberSaveable { mutableStateOf("") }
    // A decision in flight locks both controls: one request, one verdict, no double submit. A pull
    // to refresh over loaded content is `isLoading` too, so the guard is narrowed to a live request.
    val isSubmitting = isLoading && request != null

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(com.thedavelopers.eventqr.ui.theme.UiBackground),
    ) {
        when {
            isLoading && request == null -> DetailSkeleton()

            errorMessage != null && request == null -> EmptyState(
                icon = AppIcons.Error,
                title = stringResource(R.string.ui_request_detail_error_title),
                description = errorMessage,
                actionLabel = stringResource(R.string.common_retry),
                onActionClick = onRetryClick,
                primaryAction = true,
            )

            request != null -> Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
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
                            modifier = Modifier.weight(1f).semantics { heading() },
                        )
                        Spacer(modifier = Modifier.size(grid.s8))
                        StatusBadge(status = eventRequestBadgeStatus(request.status), showIcon = false)
                    }

                    if (isAdmin) {
                        Text(
                            text = stringResource(
                                R.string.ui_request_detail_submitted_by,
                                requesterDisplay(
                                    request.requesterName,
                                    request.contactEmail,
                                    request.requesterUserId,
                                    stringResource(R.string.ui_request_detail_unknown_requester),
                                ),
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

                    // An admin decides on the requester's own words, so they sit right under the
                    // identity block instead of after the spec table. For an attendee the reason is
                    // the last thing they wrote, so it stays at the end of the page.
                    if (isAdmin) ReasonBlock(request.reasonForRequest)

                    // §6.8: a decision result stays in the flow; a toast would leave the screen silent.
                    if (!decisionMessage.isNullOrBlank()) {
                        DecisionStrip(message = decisionMessage, isError = decisionIsError)
                    }

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
                    if (!isAdmin) ReasonBlock(request.reasonForRequest)

                    Spacer(modifier = Modifier.height(grid.s32))
                }

                if (isAdmin && request.status == EventRequestStatus.PENDING) {
                    ApprovalFooter(
                        enabled = !isSubmitting,
                        onReject = {
                            remarksText = ""
                            showConfirmDialog = ConfirmAction.REJECT
                        },
                        onApprove = {
                            remarksText = ""
                            showConfirmDialog = ConfirmAction.APPROVE
                        },
                    )
                }
            }
        }
    }

    showConfirmDialog?.let { action ->
        ConfirmDialog(
            action = action,
            remarks = remarksText,
            isSubmitting = isSubmitting,
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

/**
 * First load (§5.11): bars in the loaded geometry, never a centred spinner. One TalkBack node
 * announces "Loading"; the bars themselves are decorative.
 */
@Composable
private fun DetailSkeleton() {
    val grid = LocalGrid.current
    val loading = stringResource(R.string.ui_skeleton_loading)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .semantics(mergeDescendants = true) { contentDescription = loading }
            .padding(horizontal = grid.gutter, vertical = grid.s20),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(grid.skeletonBarHeight)
                .background(UiSurfaceSunken),
        )
        Spacer(modifier = Modifier.height(grid.s12))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.4f)
                .height(grid.skeletonBarHeight)
                .background(UiSurfaceSunken),
        )
        Spacer(modifier = Modifier.height(grid.s24))
        repeat(5) {
            Row(
                modifier = Modifier.fillMaxWidth().height(grid.metricRowHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .weight(0.35f)
                        .height(grid.skeletonBarHeight)
                        .background(UiSurfaceSunken),
                )
                Spacer(modifier = Modifier.weight(0.3f))
                Box(
                    modifier = Modifier
                        .weight(0.35f)
                        .height(grid.skeletonBarHeight)
                        .background(UiSurfaceSunken),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimensionResource(R.dimen.stroke_hairline))
                    .background(UiHairline),
            )
        }
    }
}

/** 72dp sticky footer (§6.7): destructive-outline Reject, 8dp gap, primary Approve last. */
@Composable
private fun ApprovalFooter(
    enabled: Boolean,
    onReject: () -> Unit,
    onApprove: () -> Unit,
) {
    val grid = LocalGrid.current
    // The window is edge to edge, so the bar's fill has to run behind the gesture bar while its
    // content stays above it - the XML form footers do the same with their navigation-bar inset.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(UiSurface)
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(dimensionResource(R.dimen.stroke_hairline))
                .background(UiHairline),
        )
        Row(
            // A minimum, not a fixed height: at a large font scale the bar grows instead of
            // clipping its labels.
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = dimensionResource(R.dimen.ui_footer_height))
                .padding(horizontal = grid.gutter, vertical = grid.s12),
            horizontalArrangement = Arrangement.spacedBy(grid.s8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UiButton(
                text = stringResource(R.string.ui_request_detail_reject),
                onClick = onReject,
                modifier = Modifier.weight(1f),
                tone = UiButtonTone.DESTRUCTIVE,
                enabled = enabled,
            )
            UiButton(
                text = stringResource(R.string.ui_request_detail_approve),
                onClick = onApprove,
                modifier = Modifier.weight(1f),
                enabled = enabled,
            )
        }
    }
}

/** In-flow decision result with a polite live region, so the change is announced without a toast. */
@Composable
private fun DecisionStrip(message: String, isError: Boolean) {
    val grid = LocalGrid.current
    val type = LocalType.current
    Text(
        text = message,
        style = type.bodyS,
        color = if (isError) UiOnErrorContainer else UiOnSuccessContainer,
        modifier = Modifier
            .padding(top = grid.s16)
            .fillMaxWidth()
            .clip(RoundedCornerShape(grid.radiusCard))
            .background(if (isError) UiErrorContainer else UiSuccessContainer)
            .padding(grid.s12)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun SectionEyebrow(text: String) {
    val grid = LocalGrid.current
    val type = LocalType.current
    Text(
        text = text,
        style = type.overline,
        color = UiTextSecondary,
        modifier = Modifier
            .padding(top = grid.s24, bottom = grid.s8)
            .semantics { heading() },
    )
}

/** The requester's own words, under its own section label. Absent copy draws nothing. */
@Composable
private fun ReasonBlock(reason: String?) {
    if (reason.isNullOrBlank()) return
    val type = LocalType.current
    SectionEyebrow(stringResource(R.string.ui_request_detail_reason))
    Text(text = reason, style = type.body, color = UiTextPrimary)
}

/**
 * One spec-table row (§6.5 / §5.9 form 2): label left, value right, hairline underneath. The row is
 * a minimum height, not a fixed one, so a long value or a large font scale wraps and grows.
 */
@Composable
private fun SpecRow(label: String, value: String, showLabel: Boolean = true) {
    val grid = LocalGrid.current
    val type = LocalType.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = grid.metricRowHeight)
                .padding(vertical = grid.s12),
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
                // A long value wraps inside its share instead of ellipsizing the fact away. The
                // share is filled, not shrunk to the text: that is what pins every value to the
                // row's end edge, including a one-word one.
                modifier = Modifier
                    .weight(1f)
                    .padding(start = grid.s16),
            )
        }
        Box(modifier = Modifier.fillMaxWidth().height(dimensionResource(R.dimen.stroke_hairline)).background(UiHairline))
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
            fill = UiErrorContainer
            ink = UiOnErrorContainer
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
    isSubmitting: Boolean,
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
    // The commit names its own verb; it never reuses the question as its label.
    val commitLabel = stringResource(
        when (action) {
            ConfirmAction.APPROVE -> R.string.ui_request_detail_approve
            ConfirmAction.REJECT -> R.string.ui_request_detail_reject
        },
    )
    val commitColor = if (action == ConfirmAction.REJECT) UiError else UiPrimary

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(grid.radiusSheet),
        containerColor = UiSurface,
        title = { Text(text = title, style = type.title, color = UiTextPrimary, fontWeight = FontWeight.SemiBold) },
        text = {
            // A keyboard on a short screen must not clip the dialog: the body scrolls, the actions
            // stay pinned. The field grows with its text instead of trapping the caret.
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(text = body, style = type.body, color = UiTextSecondary)
                Spacer(modifier = Modifier.height(grid.s12))
                RemarksField(value = remarks, onValueChange = onRemarksChange)
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isSubmitting,
            ) {
                Text(
                    text = commitLabel,
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

/** `bg_ui_field` language in Compose: sunken 4dp-radius box, no outline, 2dp focus stroke. */
@Composable
private fun RemarksField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    val grid = LocalGrid.current
    val type = LocalType.current
    val shape = RoundedCornerShape(grid.radiusButton)
    var focused by remember { mutableStateOf(false) }
    val remarksLabel = stringResource(R.string.ui_request_detail_remarks)

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = type.body.copy(color = UiTextPrimary),
        cursorBrush = SolidColor(UiPrimary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            // The placeholder disappears once typed; the field still needs a spoken name.
            .semantics { contentDescription = remarksLabel },
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = dimensionResource(R.dimen.ui_field_multiline_height))
                    .clip(shape)
                    .background(UiSurfaceSunken)
                    .then(
                        if (focused) {
                            Modifier.border(dimensionResource(R.dimen.stroke_focus), UiPrimary, shape)
                        } else {
                            Modifier
                        },
                    )
                    .padding(grid.cardPadding),
                contentAlignment = Alignment.TopStart,
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = remarksLabel,
                        style = type.bodyS,
                        color = UiTextSecondary,
                    )
                }
                innerTextField()
            }
        },
    )
}
