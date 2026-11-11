package com.thedavelopers.eventqr.core.util

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import com.thedavelopers.eventqr.R
import com.yalantis.ucrop.UCrop
import com.yalantis.ucrop.UCropActivity
import com.yalantis.ucrop.R as UCropR
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** UCrop's screen ignores insets, so the application pads it: base padding kept, repeated dispatch not stacked. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35])
class UCropInsetsTest {

    /** The intent the app really launches, so the real UCropActivity runs its own onCreate. */
    private fun cropIntent(): Intent {
        val dir = File.createTempFile("ucrop", "").apply {
            delete()
            mkdirs()
        }
        val source = File(dir, "input.jpg").apply { writeBytes(ByteArray(64)) }
        return UCrop.of(Uri.fromFile(source), Uri.fromFile(File(dir, "output.jpg")))
            .getIntent(ApplicationProvider.getApplicationContext())
    }

    /** Plain host for the padding math; created but not started, so the root stays detached. */
    private fun cropLayoutActivity(): Activity =
        Robolectric.buildActivity(Activity::class.java).create().get().apply {
            setContentView(UCropR.layout.ucrop_activity_photobox)
        }

    private fun dispatch(root: View, left: Int = 0, top: Int = 0, right: Int = 0, bottom: Int = 0) {
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(left, top, right, bottom))
            .build()
        ViewCompat.dispatchApplyWindowInsets(root, insets)
    }

    @Test
    fun `the start callback pads the real UCrop activity after its content view exists`() {
        val controller = Robolectric.buildActivity(UCropActivity::class.java, cropIntent()).create()
        val activity = controller.get()
        // onCreate dispatches onActivityCreated before UCropActivity calls setContentView, so a hook there
        // would see no crop views at all. The hook must land on start.
        assertNull(
            activity.findViewById<View>(UCropR.id.ucrop_photobox)
                ?.getTag(R.id.eventqr_crop_insets_applied),
        )
        controller.start()

        val root = activity.findViewById<View>(UCropR.id.ucrop_photobox)
        val toolbar = activity.findViewById<View>(UCropR.id.toolbar)
        val controls = activity.findViewById<View>(UCropR.id.controls_wrapper)

        assertEquals(true, root.getTag(R.id.eventqr_crop_insets_applied))
        // The navigation-bar strip the padding exposes is painted with UCrop's own dark controls color,
        // which is what keeps the light navigation icons legible.
        val expected = ContextCompat.getColor(activity, UCropR.color.ucrop_color_widget_background)
        assertEquals(expected, (controls.background as ColorDrawable).color)

        dispatch(root, top = 24, bottom = 48)
        assertEquals(24, toolbar.paddingTop)
        assertEquals(48, controls.paddingBottom)
    }

    @Test
    fun `a second start callback does not stack the insets`() {
        val activity = Robolectric.buildActivity(UCropActivity::class.java, cropIntent()).create().start().get()
        val root = activity.findViewById<View>(UCropR.id.ucrop_photobox)
        val toolbar = activity.findViewById<View>(UCropR.id.toolbar)
        val controls = activity.findViewById<View>(UCropR.id.controls_wrapper)

        dispatch(root, top = 24, bottom = 48)
        activity.padUCropForSystemBars()
        dispatch(root, top = 24, bottom = 48)

        assertEquals(24, toolbar.paddingTop)
        assertEquals(48, controls.paddingBottom)
    }

    @Test
    fun `recreation re-captures the padding base from the layout`() {
        val controller = Robolectric.buildActivity(UCropActivity::class.java, cropIntent()).create().start()
        dispatch(controller.get().findViewById<View>(UCropR.id.ucrop_photobox), left = 10, top = 24, right = 6, bottom = 48)

        controller.recreate()

        val activity = controller.get()
        val root = activity.findViewById<View>(UCropR.id.ucrop_photobox)
        val toolbar = activity.findViewById<View>(UCropR.id.toolbar)
        val controls = activity.findViewById<View>(UCropR.id.controls_wrapper)
        assertEquals(true, root.getTag(R.id.eventqr_crop_insets_applied))

        // 10/24/6, not 20/48/12: the fresh instance captures the XML padding as its base, not the old padded view.
        // The navigation-bar bottom is asserted in the detached hosts below: on API < 29 androidx.core clamps it to
        // the root window's stable inset, which a recreated (and therefore attached) Robolectric view reports as 0.
        dispatch(root, left = 10, top = 24, right = 6, bottom = 48)
        assertEquals(10, toolbar.paddingLeft)
        assertEquals(24, toolbar.paddingTop)
        assertEquals(6, toolbar.paddingRight)
        assertEquals(10, controls.paddingLeft)
        assertEquals(6, controls.paddingRight)
    }

    @Test
    fun `the application hook leaves a non-crop activity alone`() {
        val controller = Robolectric.buildActivity(Activity::class.java).create()
        val activity = controller.get()
        // Same layout, but a plain Activity: the hook's UCrop branch must not run for it.
        activity.setContentView(UCropR.layout.ucrop_activity_photobox)
        controller.start()

        val root = activity.findViewById<View>(UCropR.id.ucrop_photobox)
        val toolbar = activity.findViewById<View>(UCropR.id.toolbar)
        val controls = activity.findViewById<View>(UCropR.id.controls_wrapper)
        assertNull(root.getTag(R.id.eventqr_crop_insets_applied))

        dispatch(root, top = 24, bottom = 48)
        assertEquals(0, toolbar.paddingTop)
        assertEquals(0, controls.paddingBottom)
    }

    @Test
    fun `toolbar and controls add the bars to their own padding`() {
        val activity = cropLayoutActivity()
        val root = activity.findViewById<View>(UCropR.id.ucrop_photobox)
        val toolbar = activity.findViewById<View>(UCropR.id.toolbar)
        val controls = activity.findViewById<View>(UCropR.id.controls_wrapper)
        toolbar.setPadding(4, 8, 12, 16)
        controls.setPadding(2, 3, 5, 7)

        activity.padUCropForSystemBars()
        // Landscape / RTL: non-zero left and right too.
        dispatch(root, left = 10, top = 24, right = 6, bottom = 48)

        assertEquals(14, toolbar.paddingLeft)
        assertEquals(32, toolbar.paddingTop)
        assertEquals(18, toolbar.paddingRight)
        assertEquals(16, toolbar.paddingBottom)
        assertEquals(12, controls.paddingLeft)
        assertEquals(3, controls.paddingTop)
        assertEquals(11, controls.paddingRight)
        assertEquals(55, controls.paddingBottom)

        // A later dispatch replaces the insets instead of adding to them.
        dispatch(root, left = 0, top = 0, right = 0, bottom = 0)
        assertEquals(4, toolbar.paddingLeft)
        assertEquals(8, toolbar.paddingTop)
        assertEquals(12, toolbar.paddingRight)
        assertEquals(16, toolbar.paddingBottom)
        assertEquals(2, controls.paddingLeft)
        assertEquals(3, controls.paddingTop)
        assertEquals(5, controls.paddingRight)
        assertEquals(7, controls.paddingBottom)
    }

    @Test
    fun `a host without the crop layout is left alone`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get().apply {
            setContentView(View(this))
        }

        activity.padUCropForSystemBars()

        assertNull(activity.findViewById<View>(UCropR.id.ucrop_photobox))
    }
}
