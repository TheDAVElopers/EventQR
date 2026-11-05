package com.thedavelopers.eventqr.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
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
 *   item: 24dp icon, 11sp/600 label always visible, 12dp vertical rhythm
 *   active: icon + label ui_primary, plus a 3dp x 24dp ui_primary bar sitting on the TOP edge of
 *           the item, aligned to the icon centre
 *   inactive: ui_text_secondary
 *   min touch target 48x48, items share the width equally (4 attendee/staff/admin, 5 organizer)
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(UiSurface),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(dimensionResource(R.dimen.stroke_hairline))
                .background(UiHairline),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
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
                    Spacer(modifier = Modifier.height(grid.s2))
                    // The item's inner box (bar height minus the 8dp paddings) is 3dp short of
                    // indicator + gap + icon + gap + line, so a plain Column clamps this Text to the
                    // remaining space (11dp) and shaves the descender. Unbounded wrap keeps the full
                    // 14sp line box; it still lands inside the bar at font scale 1.3.
                    Text(
                        text = item.label,
                        style = type.micro.copy(fontWeight = FontWeight.SemiBold),
                        color = contentColor,
                        maxLines = 1,
                        modifier = Modifier.wrapContentHeight(unbounded = true),
                    )
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
