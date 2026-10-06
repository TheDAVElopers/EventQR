package com.thedavelopers.eventqr.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.ui.theme.BrandPrimaryDark
import com.thedavelopers.eventqr.ui.theme.BrandPurple
import com.thedavelopers.eventqr.ui.theme.LocalSpacing
import com.thedavelopers.eventqr.ui.theme.TextOnPrimary
import com.thedavelopers.eventqr.ui.theme.TextOnPrimaryMuted

@Composable
fun AuthHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = spacing.extraLarge, bottomEnd = spacing.extraLarge))
            .background(
                Brush.verticalGradient(
                    colors = listOf(BrandPrimaryDark, BrandPurple),
                ),
            )
            .padding(horizontal = spacing.large, vertical = spacing.extraLarge),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(id = R.drawable.white_logo),
                contentDescription = "EventQR Logo",
                modifier = Modifier.size(spacing.brandLogoSize),
            )

            Spacer(modifier = Modifier.height(spacing.mediumSmall))

            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = TextOnPrimary,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(spacing.micro))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextOnPrimaryMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}
