package com.thedavelopers.eventqr.ui.components

import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.ui.theme.LocalGrid
import com.thedavelopers.eventqr.ui.theme.LocalType
import com.thedavelopers.eventqr.ui.theme.UiErrorContainer
import com.thedavelopers.eventqr.ui.theme.UiOnErrorContainer

/**
 * §5.16 in-flow error strip - the XML half.
 *
 * The host passes its `include_error_strip` root. One sentence, one `Retry`; the real server text
 * stays in the log (§7.5). No live region: the sentence never changes, so a repeated failure would
 * stay silent - announcing explicitly keeps every failure audible (§9.5). [announce] is false when
 * the strip is already visible: one load with several failing sources is one announcement, and the
 * sentence does not change.
 */
fun View.showInlineError(message: CharSequence, announce: Boolean = true) {
    findViewById<TextView>(R.id.txtErrorStrip).text = message
    // A styled TextView has no Button role, and there is no XML attribute for the class name, so a
    // delegate sets it on the node: TalkBack matches the Compose mirror's Role.Button.
    findViewById<TextView>(R.id.btnErrorStripRetry).apply {
        if (accessibilityDelegate == null) accessibilityDelegate = ButtonRoleDelegate
    }
    visibility = View.VISIBLE
    if (announce) announceForAccessibility(message)
}

private object ButtonRoleDelegate : View.AccessibilityDelegate() {
    override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(host, info)
        info.className = Button::class.java.name
    }
}

/**
 * §5.16 Compose mirror of `include_error_strip.xml`: `ui_error_container` fill at
 * `radiusButton`, `touchMin` tall, sentence in `bodyS`, ghost `Retry` beside it.
 * The host owns the outer margins; the strip carries none.
 */
@Composable
fun InlineErrorStrip(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val grid = LocalGrid.current
    val type = LocalType.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = grid.touchMin)
            .clip(RoundedCornerShape(grid.radiusButton))
            .background(UiErrorContainer)
            .padding(start = grid.s16, top = grid.s8, end = grid.s8, bottom = grid.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = type.bodyS,
            color = UiOnErrorContainer,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.ui_attendee_retry),
            style = type.label,
            color = UiOnErrorContainer,
            maxLines = 1,
            modifier = Modifier
                .heightIn(min = grid.buttonHeightInline)
                .clip(RoundedCornerShape(grid.radiusButton))
                .clickable(role = Role.Button, onClick = onRetry)
                .padding(horizontal = grid.s16),
        )
    }
}
