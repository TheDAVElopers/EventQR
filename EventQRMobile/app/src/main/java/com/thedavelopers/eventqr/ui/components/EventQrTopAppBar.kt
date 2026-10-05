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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thedavelopers.eventqr.ui.theme.BrandPrimary
import com.thedavelopers.eventqr.ui.theme.PaperWhite
import com.thedavelopers.eventqr.ui.theme.TextMuted
import com.thedavelopers.eventqr.ui.theme.TextOnPrimary
import com.thedavelopers.eventqr.ui.theme.TextOnPrimaryMuted
import com.thedavelopers.eventqr.ui.theme.TextPrimary

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
    val defaultColors = if (isBrandHeader) {
        TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor ?: BrandPrimary,
            titleContentColor = titleContentColor ?: TextOnPrimary,
            navigationIconContentColor = titleContentColor ?: TextOnPrimary,
            actionIconContentColor = titleContentColor ?: TextOnPrimary,
        )
    } else {
        TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor ?: PaperWhite,
            titleContentColor = titleContentColor ?: TextPrimary,
            navigationIconContentColor = titleContentColor ?: TextPrimary,
            actionIconContentColor = titleContentColor ?: TextPrimary,
        )
    }

    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isBrandHeader) TextOnPrimary else TextPrimary,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isBrandHeader) TextOnPrimaryMuted else TextMuted,
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
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(24.dp),
                            tint = if (isBrandHeader) TextOnPrimary else TextPrimary,
                        )
                    }
                }
            }
        },
        actions = actions,
        colors = defaultColors,
    )
}
