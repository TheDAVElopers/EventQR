package com.thedavelopers.eventqr.ui.components

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** §6.4/§9.3: the threshold is strictly above 1.3, so 1.3 itself keeps the shared label/value row. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MetricRowStackingTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun stacks_onlyAboveTheThreshold() {
        assertFalse(MetricRowStacking.stacks(1.0f))
        assertFalse(MetricRowStacking.stacks(1.3f))
        assertTrue(MetricRowStacking.stacks(1.31f))
        assertTrue(MetricRowStacking.stacks(2.0f))
    }

    /** Rebinding must not accumulate margins: every stacked parameter is set, never added. */
    @Test
    fun applyTo_isIdempotent() {
        val row = LayoutInflater.from(context)
            .inflate(R.layout.activity_organizer_event_details, LinearLayout(context), false)
            .findViewById<LinearLayout>(R.id.oedRowEventTime)
        val label = row.getChildAt(0) as android.widget.TextView
        val value = row.findViewById<View>(R.id.oedEventTimeValue) as android.widget.TextView
        val gap = context.resources.getDimensionPixelSize(R.dimen.space_4)

        MetricRowStacking.applyTo(row, label, value, gap)
        MetricRowStacking.applyTo(row, label, value, gap)

        assertEquals(gap, (value.layoutParams as LinearLayout.LayoutParams).topMargin)
        assertEquals(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (label.layoutParams as LinearLayout.LayoutParams).width,
        )
    }
}
