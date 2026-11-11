package com.thedavelopers.eventqr.ui.components

import android.content.Context
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * `list_row_date` is inflated by four hosts (Discover, both dashboards, Manage events); the binder
 * owns the measured divider inset so none of them can drift back to the fixed 76dp token.
 *
 * Robolectric's legacy text measurement reports one pixel per character (see
 * `PassHeadCountAutosizeTest`), so the 44dp minWidth is cleared here and the measured block decides.
 * The binder contract - inset = 20dp + block + 16dp, re-measured on every bind - is what is asserted.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class ListRowDateBinderTest {

    private fun context(): Context =
        ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_EventQR)

    private fun row(): View {
        val parent = FrameLayout(context())
        return LayoutInflater.from(parent.context).inflate(R.layout.list_row_date, parent, false)
    }

    private fun View.bind(day: String, month: String) {
        findViewById<TextView>(R.id.listRowDay).text = day
        findViewById<TextView>(R.id.listRowMonth).text = month
        ListRowBinder.bind(this, title = "DevFest Manila 2026", meta = "9:00 AM")
        layoutAt360()
    }

    private fun View.layoutAt360() {
        val width = (360 * resources.displayMetrics.density).toInt()
        measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        layout(0, 0, measuredWidth, measuredHeight)
    }

    private fun View.dateBlockWidth(): Int = findViewById<View>(R.id.listRowDateBlock).width

    private fun View.dividerInset(): Int =
        (findViewById<View>(R.id.listRowDivider).layoutParams as ViewGroup.MarginLayoutParams).marginStart

    private fun View.expectedInset(): Int {
        return resources.getDimensionPixelSize(R.dimen.ui_row_start) +
            dateBlockWidth() + resources.getDimensionPixelSize(R.dimen.space_lg)
    }

    /** Item 7: a real event name fits beside the badge on two lines before it ellipsizes. */
    @Test
    fun titleAllowsTwoLinesBeforeItEllipsizes() {
        val title = row().findViewById<TextView>(R.id.listRowTitle)

        assertEquals(2, title.maxLines)
        assertEquals(TextUtils.TruncateAt.END, title.ellipsize)
    }

    /** A long date grows the block; rebinding the same row to a short date must shrink and realign it. */
    @Test
    @Config(fontScale = 2.0f)
    fun dividerTracksTheMeasuredDateBlock_andRealignsWhenTheRowIsRebound() {
        val row = row()
        row.findViewById<View>(R.id.listRowDateBlock).minimumWidth = 0

        row.bind(day = "28", month = "SEPTEMBER")
        val wide = row.dateBlockWidth()
        val wideInset = row.dividerInset()
        assertEquals(row.expectedInset(), wideInset)

        row.bind(day = "1", month = "MAY")
        val narrow = row.dateBlockWidth()

        assertTrue("the date block must actually shrink between the two binds", narrow < wide)
        assertTrue("the inset must follow the block, not keep the first bind's value", row.dividerInset() < wideInset)
        assertEquals(row.expectedInset(), row.dividerInset())
    }
}
