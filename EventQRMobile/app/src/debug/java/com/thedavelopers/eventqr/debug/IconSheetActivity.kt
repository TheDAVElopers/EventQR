package com.thedavelopers.eventqr.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.thedavelopers.eventqr.ui.theme.EventQrTheme
import com.thedavelopers.eventqr.ui.theme.UiBackground
import com.thedavelopers.eventqr.ui.theme.UiTextPrimary
import com.thedavelopers.eventqr.ui.theme.UiTextSecondary

/**
 * Debug-only icon sheet (`MyFiles/design/05-icon-system.md` §7.7): every `ic_ui_*` at its shipped
 * 24dp, in manifest order, on `ui_background`. One screenshot proves the family is one family, and
 * a wrong glyph shows up as a mismatch instead of a rumour. Release builds never see this screen.
 *
 * The list is read off `R.drawable` so it cannot drift from the drawable folder.
 */
class IconSheetActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val icons = R_ICONS
        setContent {
            EventQrTheme {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.background(UiBackground),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(icons) { (name, res) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painter = painterResource(res),
                                contentDescription = null,
                                tint = UiTextPrimary,
                                modifier = Modifier.size(24.dp),
                            )
                            Row(modifier = Modifier.padding(top = 4.dp)) {
                                Text(
                                    text = name.removePrefix("ic_ui_"),
                                    color = UiTextSecondary,
                                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private companion object {
        val R_ICONS: List<Pair<String, Int>> =
            com.thedavelopers.eventqr.R.drawable::class.java.declaredFields
                .filter { it.name.startsWith("ic_ui_") }
                .sortedBy { it.name }
                .map { it.name to it.getInt(null) }
    }
}
