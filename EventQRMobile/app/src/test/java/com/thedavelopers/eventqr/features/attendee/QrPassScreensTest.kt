package com.thedavelopers.eventqr.features.attendee

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.QrDeliveryStatus
import com.thedavelopers.eventqr.core.api.dto.QrDisplayStatus
import com.thedavelopers.eventqr.core.api.dto.RegistrationStatus
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.qrcredential.model.dto.QrCredentialSnapshot
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse
import com.thedavelopers.eventqr.ui.components.EventQrEmptyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class QrPassScreensTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun cachedPass() = CachedQrPass(
        qrValue = "qr-value",
        eventName = "DevFest Manila",
        serial = "R-000123",
        meta = "12 Oct 2026 - PICC, Pasay",
        eventEndAtMillis = Instant.now().plus(2, ChronoUnit.HOURS).toEpochMilli(),
        savedAtMillis = Instant.now().toEpochMilli(),
    )

    private fun registration(registrationId: UUID) = RegistrationResponse(
        registrationId = registrationId,
        eventId = UUID.randomUUID(),
        attendeeUserId = UUID.randomUUID(),
        attendeeEmail = "a@test.com",
        attendeeName = "A",
        status = RegistrationStatus.REGISTERED,
        eventTitle = "DevFest Manila",
        eventEndAt = Instant.now().plus(2, ChronoUnit.HOURS),
        registrationNumber = 123,
    )

    private fun snapshot(registrationId: UUID, active: Boolean, status: QrDisplayStatus) = QrCredentialSnapshot(
        qrCredentialId = UUID.randomUUID(),
        eventId = UUID.randomUUID(),
        attendeeUserId = UUID.randomUUID(),
        registrationId = registrationId,
        qrValue = "qr-value",
        active = active,
        displayStatus = status,
        deliveryStatus = QrDeliveryStatus.SENT,
        downloaded = false,
    )

    /** The extras are on the intent before `onCreate`, so the real cache-first load runs. */
    private fun qrDisplay(registrationId: String? = null): QrDisplayActivity {
        val intent = Intent(context, QrDisplayActivity::class.java)
            .putExtra(EXTRA_REGISTRATION_ID, registrationId.orEmpty())
        return Robolectric.buildActivity(QrDisplayActivity::class.java, intent).create().get()
    }

    private fun credentialScreen(registrationId: String? = null): AttendeeQrCredentialActivity {
        val intent = Intent(context, AttendeeQrCredentialActivity::class.java)
            .putExtra(EXTRA_REGISTRATION_ID, registrationId.orEmpty())
        return Robolectric.buildActivity(AttendeeQrCredentialActivity::class.java, intent).create().get()
    }

    /**
     * The cache read and the bind run through `lifecycleScope` + `Dispatchers.IO`; a paused
     * Robolectric looper needs pumping before the main-thread continuation can run.
     */
    private fun awaitCondition(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(POLL_MS)
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(what, true, condition())
    }

    private fun awaitVisible(view: View) =
        awaitCondition("expected the view on screen") { view.visibility == View.VISIBLE }

    /**
     * Joins the screen's real cache job instead of polling the store. Polling is not neutral:
     * [QrPassCache.load] clears an entry it cannot read yet, and that clear can commit after the
     * write and erase it, which is the flake this replaces.
     */
    private fun awaitCache(activity: QrDisplayActivity, registrationId: String, present: Boolean) {
        val job = requireNotNull(activity.lastCacheJob) {
            "the screen must schedule its cache write before the test awaits it"
        }
        runBlocking { job.join() }
        assertEquals(
            "expected the cached pass present=$present",
            present,
            QrPassCache.load(context, registrationId) != null,
        )
    }

    private fun pass(activity: Activity): View = activity.findViewById(R.id.qrCredential)
    private fun empty(activity: Activity): EventQrEmptyState = activity.findViewById(R.id.emptyQrPassState)
    private fun savedCopy(activity: Activity): View = activity.findViewById(R.id.txtQrSavedCopy)
    private fun saveGroup(activity: Activity): View = activity.findViewById(R.id.groupSavePass)
    private fun badge(activity: Activity): TextView = activity.findViewById(R.id.qrValidBadge)

    @Test
    fun aCachedPassRendersInsteadOfTheErrorState() {
        val registrationId = UUID.randomUUID().toString()
        QrPassCache.save(context, registrationId, cachedPass())
        val activity = qrDisplay(registrationId)
        awaitVisible(pass(activity))

        activity.showLoadError("Network error. Please check your connection and try again.")

        assertEquals(View.VISIBLE, pass(activity).visibility)
        assertEquals(View.VISIBLE, savedCopy(activity).visibility)
        assertEquals(View.VISIBLE, saveGroup(activity).visibility)
        assertEquals(View.GONE, empty(activity).visibility)
        assertNotNull(activity.findViewById<ImageView>(R.id.qrImage).drawable)
        assertEquals("R-000123", activity.findViewById<TextView>(R.id.qrSerial).text.toString())
    }

    // A saved copy never wears the lime "Valid" badge: only a live response can say that.
    @Test
    fun aCachedPassRendersTheNeutralSavedCopyBadge() {
        val registrationId = UUID.randomUUID().toString()
        QrPassCache.save(context, registrationId, cachedPass())
        val activity = qrDisplay(registrationId)
        awaitVisible(pass(activity))

        assertEquals(activity.getString(R.string.ui_credential_saved_copy), badge(activity).text.toString())
    }

    // Rotation destroys and recreates the screen while offline: the cached pass must be on the
    // plate before any network answer lands.
    @Test
    fun aRotationShowsTheCachedPassBeforeTheNetworkAnswers() {
        val registrationId = UUID.randomUUID().toString()
        QrPassCache.save(context, registrationId, cachedPass())
        val controller = Robolectric.buildActivity(
            QrDisplayActivity::class.java,
            Intent(context, QrDisplayActivity::class.java)
                .putExtra(EXTRA_REGISTRATION_ID, registrationId),
        ).setup()
        val before = controller.get()
        awaitVisible(pass(before))

        controller.recreate()
        val rotated = controller.get()
        awaitVisible(pass(rotated))

        assertEquals(View.VISIBLE, savedCopy(rotated).visibility)
        assertEquals(View.GONE, empty(rotated).visibility)
        assertEquals(rotated.getString(R.string.ui_credential_saved_copy), badge(rotated).text.toString())
    }

    @Test
    fun noCachedPassShowsOneInlineErrorWithRetry() {
        val activity = qrDisplay()

        activity.showLoadError("Network error. Please check your connection and try again.")

        assertEquals(View.GONE, pass(activity).visibility)
        assertEquals(View.GONE, saveGroup(activity).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.rowKeepBright).visibility)
        assertEquals(View.GONE, savedCopy(activity).visibility)
        assertEquals(View.VISIBLE, empty(activity).visibility)
        assertEquals(activity.getString(R.string.ui_attendee_pass_error_title), empty(activity).title.toString())
        val retry = empty(activity).findViewById<TextView>(R.id.emptyAction)
        assertEquals(View.VISIBLE, retry.visibility)
        assertEquals(activity.getString(R.string.ui_attendee_retry), retry.text.toString())
    }

    @Test
    fun aSuccessfulRenderWritesTheCache() {
        val registrationId = UUID.randomUUID()
        val activity = qrDisplay()

        activity.renderQr(
            snapshot(registrationId, active = true, status = QrDisplayStatus.SHOWN_ONCE),
            registration(registrationId),
            "DevFest Manila",
        )
        awaitCache(activity, registrationId.toString(), present = true)

        val cached = QrPassCache.load(context, registrationId.toString())
        assertNotNull(cached)
        assertEquals("qr-value", cached!!.qrValue)
        assertEquals("DevFest Manila", cached.eventName)
        assertEquals(View.VISIBLE, pass(activity).visibility)
        assertEquals(View.GONE, empty(activity).visibility)
        assertEquals(View.GONE, savedCopy(activity).visibility)
        // A live response takes the badge back from a saved copy.
        assertEquals(activity.getString(R.string.ui_credential_valid), badge(activity).text.toString())
    }

    @Test
    fun aRevokedPassRendersNoQrAndDropsTheCachedCopy() {
        val registrationId = UUID.randomUUID()
        QrPassCache.save(context, registrationId.toString(), cachedPass())
        val activity = qrDisplay()

        activity.renderQr(
            snapshot(registrationId, active = true, status = QrDisplayStatus.REVOKED),
            registration(registrationId),
            "DevFest Manila",
        )
        awaitCache(activity, registrationId.toString(), present = false)

        assertEquals(View.GONE, pass(activity).visibility)
        assertEquals(View.GONE, saveGroup(activity).visibility)
        assertEquals(View.GONE, savedCopy(activity).visibility)
        assertEquals(View.VISIBLE, empty(activity).visibility)
        assertEquals(activity.getString(R.string.ui_attendee_pass_revoked_title), empty(activity).title.toString())
        assertNull(activity.findViewById<ImageView>(R.id.qrImage).drawable)
        assertNull(QrPassCache.load(context, registrationId.toString()))
        // A revoked pass is terminal: Retry would only repeat the same answer.
        assertEquals(View.GONE, empty(activity).findViewById<View>(R.id.emptyAction).visibility)
    }

    @Test
    fun anInactivePassRendersNoQrAndDropsTheCachedCopy() {
        val registrationId = UUID.randomUUID()
        QrPassCache.save(context, registrationId.toString(), cachedPass())
        val activity = qrDisplay()

        activity.renderQr(
            snapshot(registrationId, active = false, status = QrDisplayStatus.SHOWN_ONCE),
            registration(registrationId),
            "DevFest Manila",
        )
        awaitCache(activity, registrationId.toString(), present = false)

        assertEquals(View.GONE, pass(activity).visibility)
        assertNull(activity.findViewById<ImageView>(R.id.qrImage).drawable)
        assertNull(QrPassCache.load(context, registrationId.toString()))
    }

    @Test
    fun theCredentialScreenRendersTheCachedPassOnFailure() {
        val registrationId = UUID.randomUUID().toString()
        QrPassCache.save(context, registrationId, cachedPass())
        val activity = credentialScreen(registrationId)
        awaitVisible(pass(activity))

        activity.showLoadError("Network error. Please check your connection and try again.")

        assertEquals(View.VISIBLE, pass(activity).visibility)
        assertEquals(View.VISIBLE, savedCopy(activity).visibility)
        assertEquals(View.GONE, empty(activity).visibility)
    }

    @Test
    fun theCredentialScreenHidesARevokedPass() {
        val registrationId = UUID.randomUUID()
        val activity = credentialScreen()

        activity.renderQr(
            snapshot(registrationId, active = true, status = QrDisplayStatus.REVOKED),
            registration(registrationId),
            "DevFest Manila",
        )

        assertEquals(View.GONE, pass(activity).visibility)
        assertEquals(View.VISIBLE, empty(activity).visibility)
        assertNull(activity.findViewById<ImageView>(R.id.qrImage).drawable)
    }

    @Test
    fun theValidBadgeGrowsWithTheFontInsteadOfClipping() {
        val activity = qrDisplay()
        val badge = activity.findViewById<TextView>(R.id.qrValidBadge)

        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, badge.layoutParams.width)
        assertEquals(
            activity.resources.getDimensionPixelSize(R.dimen.ui_badge_height),
            badge.minHeight,
        )
    }

    private companion object {
        const val AWAIT_TIMEOUT_MS = 5_000L
        const val POLL_MS = 5L
    }
}
