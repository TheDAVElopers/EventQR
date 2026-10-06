package com.thedavelopers.eventqr.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.thedavelopers.eventqr.ui.theme.BrandPrimary
import com.thedavelopers.eventqr.ui.theme.LocalSpacing
import com.thedavelopers.eventqr.ui.theme.TextOnPrimary
import com.thedavelopers.eventqr.ui.theme.TextOnPrimaryMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventQrTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBackClick: (() -> Unit)? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    isBrandHeader: Boolean = false,
    containerColor: Color? = null,
    titleContentColor: Color? = null,
) {
    val spacing = LocalSpacing.current
    val colorScheme = MaterialTheme.colorScheme
    val resolvedContainerColor = remember(containerColor, isBrandHeader, colorScheme) {
        containerColor ?: if (isBrandHeader) BrandPrimary else colorScheme.surface
    }
    val resolvedTitleColor = remember(titleContentColor, isBrandHeader, colorScheme) {
        titleContentColor ?: if (isBrandHeader) TextOnPrimary else colorScheme.onSurface
    }
    val resolvedSubtitleColor = remember(isBrandHeader, colorScheme) {
        if (isBrandHeader) TextOnPrimaryMuted else colorScheme.onSurfaceVariant
    }

    val defaultColors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(
        containerColor = resolvedContainerColor,
        titleContentColor = resolvedTitleColor,
        navigationIconContentColor = resolvedTitleColor,
        actionIconContentColor = resolvedTitleColor,
    )

    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = resolvedTitleColor,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = resolvedSubtitleColor,
                    )
                }
            }
        },
        modifier = modifier,
        navigationIcon = {
            when {
                navigationIcon != null -> navigationIcon()
                onBackClick != null -> {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.size(spacing.minTouchTarget),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(spacing.iconSizeLarge),
                            tint = resolvedTitleColor,
                        )
                    }
                }
            }
        },
        actions = actions,
        colors = defaultColors,
    )
}
