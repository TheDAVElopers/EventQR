package com.thedavelopers.eventqr.debug

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.thedavelopers.eventqr.R

/**
 * Debug-only: the one mark at 16, 24, 32, 48 and 96dp on the flat brand fill, so the 16dp floor and
 * the W/8 crispness claim (`MyFiles/design/04-logo-direction.md` §5, C1) can be captured and measured.
 * Release builds never see this screen.
 */
class MarkPreviewActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mark_preview)
    }
}
