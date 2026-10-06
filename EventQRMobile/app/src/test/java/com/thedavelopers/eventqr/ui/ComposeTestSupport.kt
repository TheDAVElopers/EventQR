package com.thedavelopers.eventqr.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.thedavelopers.eventqr.ui.theme.EventQrSpacing
import com.thedavelopers.eventqr.ui.theme.EventQrTheme
import com.thedavelopers.eventqr.ui.theme.LocalSpacing

fun ComposeContentTestRule.themeColorScheme(
    darkTheme: Boolean = false,
    spacing: EventQrSpacing = EventQrSpacing(),
    content: @Composable () -> Unit,
): ColorScheme {
    var captured: ColorScheme? = null
    setContent {
        EventQrTheme(darkTheme = darkTheme, spacing = spacing) {
            captured = MaterialTheme.colorScheme
            content()
        }
    }
    waitForIdle()
    return requireNotNull(captured) { "EventQrTheme did not compose" }
}

fun ComposeContentTestRule.resolvedSpacing(
    darkTheme: Boolean = false,
    spacing: EventQrSpacing = EventQrSpacing(),
): EventQrSpacing {
    var captured: EventQrSpacing? = null
    setContent {
        EventQrTheme(darkTheme = darkTheme, spacing = spacing) {
            captured = LocalSpacing.current
        }
    }
    waitForIdle()
    return requireNotNull(captured) { "EventQrTheme did not compose" }
}