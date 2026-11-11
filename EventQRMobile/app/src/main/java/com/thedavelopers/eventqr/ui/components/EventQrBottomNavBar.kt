package com.thedavelopers.eventqr.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.ui.icons.AppIcons
import com.thedavelopers.eventqr.ui.theme.LocalGrid
import com.thedavelopers.eventqr.ui.theme.LocalType
import com.thedavelopers.eventqr.ui.theme.UiHairline
import com.thedavelopers.eventqr.ui.theme.UiPrimary
import com.thedavelopers.eventqr.ui.theme.UiSurface
import com.thedavelopers.eventqr.ui.theme.UiTextSecondary

/*
 * THE bottom navigation (§5.7). One composable and one role config list - not four copies.
 *
 *   64dp + navigation inset, ui_surface, 1dp hairline on top
 *   NOT floating, NOT rounded, NO shadow (the old nav was a raised card with a deeper shadow
 *   than any content on the screen, which is the single clearest "templated" tell in the app)
 *   item: 24dp icon, 11sp/600 label, space_8 top and bottom (8+3+8+24+2+14 = 59dp inside 64dp)
 *   active: icon + label ui_primary, plus a 3dp x 24dp ui_primary bar sitting on the TOP edge of
 *           the item, aligned to the icon centre
 *   inactive: ui_text_secondary
 *   min touch target 48x48, items share the width equally (4 attendee/staff/admin, 5 organizer)
 *
 * Labels are dropped by measurement, not by a fixed font-scale constant: each label is measured
 * once at the current width and font scale in the label style (micro 11sp/600), and the bar shows
 * labels only while the widest one still fits its slot minus space_8. One long label drops the whole
 * bar's labels, never a single item, so the bar stays symmetrical; a clipped label ("Dashboa") is
 * worse than none. The item keeps its selectable semantics and carries the label as its
 * contentDescription, so TalkBack still reads it. Bar height is unchanged - the icon does not move
 * between the two states.
 *
 * The icon is a `@DrawableRes` from `AppIcons` (design/05-icon-system.md): one family, one address.
 */

data class NavItem(
    val id: String,
    val label: String,
    @DrawableRes val icon: Int,
)

@Composable
fun EventQrBottomNavBar(
    items: List<NavItem>,
    selectedId: String,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val grid = LocalGrid.current
    val type = LocalType.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    // One style measured and rendered: if these drift, the guard passes and the label still clips.
    val labelStyle = type.micro.copy(fontWeight = FontWeight.SemiBold)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(UiSurface),
    ) {
        // Items are weight(1f) in a row with no horizontal padding, so every slot is the same.
        val slotWidth = maxWidth / items.size
        val widestLabel = remember(items, maxWidth, labelStyle, density) {
            items.maxOfOrNull { item ->
                textMeasurer.measure(
                    text = AnnotatedString(item.label),
                    style = labelStyle,
                    maxLines = 1,
                    softWrap = false,
                ).size.width
            }
        }
        // Unbounded width means every slot is infinite: never claim the labels fit.
        val showLabels = widestLabel != null && constraints.hasBoundedWidth &&
            with(density) { widestLabel.toDp() } <= slotWidth - grid.s8

        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimensionResource(R.dimen.stroke_hairline))
                    .background(UiHairline),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // Vertical only: a landscape 3-button nav bar adds horizontal insets, which
                    // would shrink every slot under the measured width the label guard checked.
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Vertical))
                    .height(grid.navHeight),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { item ->
                    val isSelected = item.id == selectedId
                    val contentColor = if (isSelected) UiPrimary else UiTextSecondary
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .selectable(
                                selected = isSelected,
                                role = Role.Tab,
                                onClick = { onItemSelected(item.id) },
                            )
                            .then(
                                if (showLabels) Modifier
                                else Modifier.semantics { contentDescription = item.label },
                            )
                            .padding(top = grid.s8, bottom = grid.s8),
                    ) {
                        // The indicator sits ON the top edge of the item, aligned to the icon centre.
                        Box(
                            modifier = Modifier
                                .width(grid.navIndicatorWidth)
                                .height(grid.navIndicatorHeight)
                                .then(if (isSelected) Modifier.background(UiPrimary) else Modifier),
                        )
                        Spacer(modifier = Modifier.height(grid.s8))
                        Icon(
                            painter = painterResource(item.icon),
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(grid.navIcon),
                        )
                        if (showLabels) {
                            Spacer(modifier = Modifier.height(grid.s2))
                            // The item's inner box (bar height minus the 8dp paddings) is 3dp short
                            // of indicator + gap + icon + gap + line, so a plain Column clamps this
                            // Text to the remaining space (11dp) and shaves the descender.
                            // Unbounded wrap keeps the full 14sp line box; it still lands inside the
                            // bar while the measured label fits.
                            Text(
                                text = item.label,
                                style = labelStyle,
                                color = contentColor,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.wrapContentHeight(unbounded = true),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Attendee bar. Five destinations (Home / Events / Registered / Rewards / Profile) because the
 * attendee shell has five screens today; §5.7 budgets four for most roles and five for organizer.
 * Keep the ids - they are the navigation keys.
 */
val AttendeeNavItems = listOf(
    NavItem("home", "Home", AppIcons.Home),
    NavItem("events", "Events", AppIcons.Event),
    NavItem("registered", "Registered", AppIcons.Pass),
    NavItem("rewards", "Rewards", AppIcons.Reward),
    NavItem("profile", "Profile", AppIcons.Profile),
)

val StaffNavItems = listOf(
    NavItem("dashboard", "Dashboard", AppIcons.Home),
    NavItem("scanner", "Scan", AppIcons.Scan),
    NavItem("events", "Events", AppIcons.Event),
    NavItem("logs", "Logs", AppIcons.Logs),
)

/**
 * Admin portal items. The super-admin portal reuses the same screens, so SUPER_ADMIN accounts
 * bind AdminNavItems as well - see
 * [com.thedavelopers.eventqr.features.admin.configureAdminBottomNav].
 */
val AdminNavItems = listOf(
    NavItem("dashboard", "Dashboard", AppIcons.Home),
    NavItem("requests", "Requests", AppIcons.Request),
    NavItem("accounts", "Accounts", AppIcons.People),
    NavItem("logs", "Logs", AppIcons.Logs),
)

val OrganizerNavItems = listOf(
    NavItem("dashboard", "Dashboard", AppIcons.Home),
    NavItem("events", "Events", AppIcons.Event),
    NavItem("attendees", "Attendees", AppIcons.People),
    NavItem("reports", "Reports", AppIcons.Report),
    NavItem("rewards", "Rewards", AppIcons.Reward),
)
