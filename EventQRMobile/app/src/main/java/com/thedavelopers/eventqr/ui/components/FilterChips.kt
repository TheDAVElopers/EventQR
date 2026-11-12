package com.thedavelopers.eventqr.ui.components

import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import androidx.core.view.ViewCompat

/**
 * §5.6 / §9.5 accessibility for XML filter chips.
 *
 * A styled `TextView` chip has no Button role, and there is no XML attribute for the class name, so
 * a delegate sets it on the node (the same [ButtonRoleDelegate] the inline error strip's Retry
 * uses) and mirrors [View.isSelected] so TalkBack announces "selected". A chip's state is carried by
 * [View.isSelected] alone: the fill and label colour follow from the same flag through `bg_ui_chip`
 * and `ui_chip_text`. No code swaps backgrounds or colours.
 *
 * Call once per chip after inflation. TalkBack traversal is geometric (top to bottom, start to end),
 * so the order matches the XML only because a chip row's `constraint_referenced_ids` lists the
 * chips in XML order and the packed, start-aligned Flow lays them out in that order.
 */
fun View.asFilterChip() = asButtonRole()

/** [asFilterChip] for each of [chips]. */
fun asFilterChips(vararg chips: View) {
    chips.forEach { it.asFilterChip() }
}

/**
 * Gives a styled non-Button view the Button role. A view that already carries a delegate keeps it:
 * the guard never replaces another delegate. `ViewCompat.hasAccessibilityDelegate` is used because
 * `View.getAccessibilityDelegate` needs API 29 and minSdk is 26.
 */
internal fun View.asButtonRole() {
    if (!ViewCompat.hasAccessibilityDelegate(this)) setAccessibilityDelegate(ButtonRoleDelegate)
}

/**
 * The one Button-role delegate: the class name TalkBack reads as the role, plus the selected flag.
 * The base `View` does not put `isSelected` on the node (a unit test proves it), so without the
 * second line a selected chip would not be announced as selected. A view that is never selected
 * (the error strip's Retry) reports false, so sharing the delegate changes nothing for it.
 */
internal object ButtonRoleDelegate : View.AccessibilityDelegate() {
    override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(host, info)
        info.className = Button::class.java.name
        info.isSelected = host.isSelected
    }
}
