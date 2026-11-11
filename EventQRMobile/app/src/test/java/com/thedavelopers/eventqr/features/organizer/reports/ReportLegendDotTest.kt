package com.thedavelopers.eventqr.features.organizer.reports

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The chart legend tints its dot with the span colour. This builds the real row and paints its dot,
 * so the assertion covers the call site: reverting the legend to `bg_ui_dot` inherits that
 * drawable's 60% alpha and fails here (§6.4).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReportLegendDotTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val sliceColor = ContextCompat.getColor(context, R.color.chart_1)

    @Test
    fun legendDot_paintsAtFullStrength() {
        val dot = (legendRow(
            context = context,
            label = "Registered",
            valueText = "12",
            percentText = context.getString(R.string.report_preview_legend_percent, "40.0"),
            sliceColor = sliceColor,
        ) as ViewGroup).getChildAt(0)

        val painted = paintCenter(dot)

        assertEquals(255, Color.alpha(painted))
        // Full strength means the slice colour exactly, not a pale wash of it.
        assertEquals(sliceColor, painted)
    }

    private fun paintCenter(view: View): Int {
        val size = context.resources.getDimensionPixelSize(R.dimen.ui_badge_dot)
        view.measure(
            MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap.getPixel(size / 2, size / 2)
    }
}
