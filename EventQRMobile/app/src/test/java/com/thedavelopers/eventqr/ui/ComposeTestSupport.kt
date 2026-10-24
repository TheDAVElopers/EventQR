package com.thedavelopers.eventqr.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import android.content.Context
import androidx.annotation.ColorRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.test.core.app.ApplicationProvider
import com.thedavelopers.eventqr.ui.theme.EventQrSpacing
import com.thedavelopers.eventqr.ui.theme.EventQrTheme
import com.thedavelopers.eventqr.ui.theme.LocalSpacing

fun ComposeContentTestRule.themeColorScheme(
    spacing: EventQrSpacing? = null,
    content: @Composable () -> Unit,
): ColorScheme {
    var captured: ColorScheme? = null
    setContent {
        EventQrTheme(spacing = spacing) {
            captured = MaterialTheme.colorScheme
            content()
        }
    }
    waitForIdle()
    return requireNotNull(captured) { "EventQrTheme did not compose" }
}

fun ComposeContentTestRule.resolvedSpacing(
    spacing: EventQrSpacing? = null,
): EventQrSpacing {
    var captured: EventQrSpacing? = null
    setContent {
        EventQrTheme(spacing = spacing) {
            captured = LocalSpacing.current
        }
    }
    waitForIdle()
    return requireNotNull(captured) { "EventQrTheme did not compose" }
}

/** Resolves a colour role (`R.color.primary`, ...) outside composition, for asserting against the Compose bridge. */
fun roleColor(@ColorRes id: Int): Color =
    Color(ApplicationProvider.getApplicationContext<Context>().getColor(id))

/**
 * Runs [block] once inside [EventQrTheme] and returns its result. The Compose colour names in ui/theme/Color.kt
 * are composable (they resolve Android colour resources), so tests read them through this.
 */
fun <T> ComposeContentTestRule.inComposition(block: @Composable () -> T): T {
    var captured: Any? = null
    setContent {
        EventQrTheme {
            captured = block()
        }
    }
    waitForIdle()
    @Suppress("UNCHECKED_CAST")
    return captured as T
}
