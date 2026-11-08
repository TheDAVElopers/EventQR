package com.thedavelopers.eventqr.features.attendee

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.appcompat.view.ContextThemeWrapper
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.events.model.dto.AttendeeEventResponse
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID

/**
 * Discover's rows get their measured divider inset from [com.thedavelopers.eventqr.ui.components.ListRowBinder],
 * not from the adapter. The growth/rebind proof lives in `ListRowDateBinderTest`; this test only
 * checks the adapter hands the row to the binder.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class AttendeeEventAdapterTest {

    private fun event() = AttendeeEventResponse(
        eventId = UUID.randomUUID(),
        title = "DevFest Manila 2026",
        capacity = 12,
        currentAttendeeCount = 0,
        eventStartAt = Instant.now().plusSeconds(86_400),
        eventEndAt = Instant.now().plusSeconds(93_600),
    )

    private fun layoutRow(): View {
        val base = ApplicationProvider.getApplicationContext<android.content.Context>()
        val context = ContextThemeWrapper(base, R.style.Theme_EventQR)
        val parent = FrameLayout(context)
        val adapter = AttendeeEventAdapter {}
        adapter.submitItems(listOf(event()))
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)
        val row = holder.itemView
        val width = (360 * context.resources.displayMetrics.density).toInt()
        row.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        row.layout(0, 0, row.measuredWidth, row.measuredHeight)
        return row
    }

    private fun View.dividerInset(): Int =
        (findViewById<View>(R.id.listRowDivider).layoutParams as ViewGroup.MarginLayoutParams).marginStart

    private fun View.expectedInset(): Int {
        val block = findViewById<View>(R.id.listRowDateBlock).width
        return resources.getDimensionPixelSize(R.dimen.ui_row_start) +
            block + resources.getDimensionPixelSize(R.dimen.space_lg)
    }

    @Test
    fun divider_alignsWithTheTextEdgeAtNormalScale() {
        val row = layoutRow()

        assertEquals(
            row.resources.getDimensionPixelSize(R.dimen.ui_row_date_block) +
                row.resources.getDimensionPixelSize(R.dimen.ui_row_start) +
                row.resources.getDimensionPixelSize(R.dimen.space_lg),
            row.dividerInset(),
        )
        assertEquals(row.expectedInset(), row.dividerInset())
    }
}
