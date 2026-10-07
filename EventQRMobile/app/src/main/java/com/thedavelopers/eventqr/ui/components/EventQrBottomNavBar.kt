package com.thedavelopers.eventqr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.thedavelopers.eventqr.ui.theme.LocalSpacing

data class NavItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
)

@Composable
fun EventQrBottomNavBar(
    items: List<NavItem>,
    selectedId: String,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val colorScheme = MaterialTheme.colorScheme

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = spacing.mediumLarge, topEnd = spacing.mediumLarge),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = spacing.navElevation),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = spacing.small, vertical = spacing.micro),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                val isSelected = item.id == selectedId
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(spacing.mediumSmall))
                        .selectable(
                            selected = isSelected,
                            role = Role.Tab,
                            onClick = { onItemSelected(item.id) },
                        )
                        .padding(
                            horizontal = spacing.mediumSmall,
                            vertical = spacing.micro,
                        ),
                ) {
                    Box(
                        modifier = Modifier
                            .size(spacing.iconContainerSmall)
                            .clip(RoundedCornerShape(spacing.mediumSmall))
                            .background(
                                if (isSelected) colorScheme.primaryContainer else Color.Transparent,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = if (isSelected) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(spacing.iconSizeMedium),
                        )
                    }
                    Spacer(modifier = Modifier.height(spacing.nano))
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        ),
                        color = if (isSelected) colorScheme.primary else colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

val AttendeeNavItems = listOf(
    NavItem("home", "Home", Icons.Default.Home),
    NavItem("events", "Events", Icons.Default.CalendarToday),
    NavItem("registered", "Registered", Icons.Default.HowToReg),
    NavItem("rewards", "Rewards", Icons.Default.CardGiftcard),
    NavItem("profile", "Profile", Icons.Default.Person),
)

val StaffNavItems = listOf(
    NavItem("dashboard", "Dashboard", Icons.Default.Home),
    NavItem("scanner", "Scan", Icons.Default.QrCodeScanner),
    NavItem("events", "Events", Icons.Default.CalendarToday),
    NavItem("logs", "Logs", Icons.Default.Description),
)

/**
 * Admin portal items. The super-admin portal reuses the same screens (admin dashboard,
 * event requests, account management, audit logs), so [SUPER_ADMIN] accounts bind
 * [AdminNavItems] as well — see [com.thedavelopers.eventqr.features.admin.configureAdminBottomNav].
 */
val AdminNavItems = listOf(
    NavItem("dashboard", "Dashboard", Icons.Default.Home),
    NavItem("requests", "Requests", Icons.Default.RequestQuote),
    NavItem("accounts", "Accounts", Icons.Default.Group),
    NavItem("logs", "Logs", Icons.Default.Description),
)

/** Organizer portal items, mirroring the destinations of the legacy organizer nav. */
val OrganizerNavItems = listOf(
    NavItem("dashboard", "Dashboard", Icons.Default.Home),
    NavItem("events", "Events", Icons.Default.CalendarToday),
    NavItem("attendees", "Attendees", Icons.Default.Group),
    NavItem("reports", "Reports", Icons.Default.Assessment),
    NavItem("rewards", "Rewards", Icons.Default.CardGiftcard),
)
