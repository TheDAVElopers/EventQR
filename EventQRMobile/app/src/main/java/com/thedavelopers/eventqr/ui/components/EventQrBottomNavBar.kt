package com.thedavelopers.eventqr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thedavelopers.eventqr.ui.theme.BrandPrimary
import com.thedavelopers.eventqr.ui.theme.PaperWhite
import com.thedavelopers.eventqr.ui.theme.StatCardPurpleBg
import com.thedavelopers.eventqr.ui.theme.TextMuted

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
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        colors = CardDefaults.cardColors(containerColor = PaperWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                val isSelected = item.id == selectedId
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onItemSelected(item.id) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) StatCardPurpleBg else PaperWhite),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = if (isSelected) BrandPrimary else TextMuted,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 10.sp,
                        ),
                        color = if (isSelected) BrandPrimary else TextMuted,
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
    NavItem("profile", "Profile", Icons.Default.Person),
)
