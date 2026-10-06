package com.thedavelopers.eventqr.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.thedavelopers.eventqr.ui.theme.LocalSpacing

@Composable
fun <T> FilterChipRow(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    labelProvider: (T) -> String = { it.toString() },
) {
    val spacing = LocalSpacing.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = spacing.screenHorizontalPadding, vertical = spacing.small),
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        items.forEach { item ->
            val isSelected = item == selectedItem
            FilterChip(
                selected = isSelected,
                onClick = { onItemSelected(item) },
                label = {
                    Text(
                        text = labelProvider(item),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        ),
                    )
                },
                modifier = Modifier.heightIn(min = spacing.minTouchTarget),
                shape = RoundedCornerShape(spacing.chipCornerRadius),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = MaterialTheme.colorScheme.outline,
                    selectedBorderColor = MaterialTheme.colorScheme.primary,
                    borderWidth = spacing.chipBorderWidth,
                    selectedBorderWidth = spacing.chipBorderWidth,
                ),
            )
        }
    }
}
