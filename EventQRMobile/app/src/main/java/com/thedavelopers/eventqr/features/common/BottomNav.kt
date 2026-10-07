package com.thedavelopers.eventqr.features.common

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.ui.components.EventQrBottomNavBar
import com.thedavelopers.eventqr.ui.components.NavItem
import com.thedavelopers.eventqr.ui.theme.EventQrTheme

/**
 * Shared entry point for hosting the Compose [EventQrBottomNavBar] inside XML screens.
 *
 * Every screen that used the legacy 76dp XML bottom-nav block now hosts a
 * [ComposeView] with id [R.id.composeBottomNav] instead. This binder wires that view:
 * theme, composition strategy, item set, active-item id and navigation callback —
 * so per-role screens stay one-liners (see `configure*BottomNav`).
 */
internal fun AppCompatActivity.bindComposeBottomNav(
    view: ComposeView,
    items: List<NavItem>,
    selectedId: String,
    onNavigate: (String) -> Unit,
) {
    view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    view.setContent {
        EventQrTheme {
            EventQrBottomNavBar(
                items = items,
                selectedId = selectedId,
                onItemSelected = onNavigate,
            )
        }
    }
}

