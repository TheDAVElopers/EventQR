package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.core.util.UiStrings
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.ui.theme.color
import com.thedavelopers.eventqr.core.api.dto.QrDisplayStatus
import com.thedavelopers.eventqr.core.util.BitmapSaver
import com.thedavelopers.eventqr.core.util.DateFormatters
import com.thedavelopers.eventqr.features.qrcredential.model.dto.QrCredentialSnapshot
import com.thedavelopers.eventqr.features.registrations.RegistrationNumberFormatter
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse
import com.thedavelopers.eventqr.ui.components.EventQrEmptyState
import com.thedavelopers.eventqr.ui.components.showInlineError
import com.thedavelopers.eventqr.ui.theme.applyEventQrBottomInsetPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * Dedicated QR display screen for attendees opening their credential
 * from the registered events list.
 *
 * Mirrors [AttendeeQrCredentialActivity] (the post-registration success screen)
 * but presents the credential with a top-left back button and a "QR Display"
 * header matching the Edit Profile screen's toolbar sizing.
 */
private val PASS_DATE_FORMATTER: java.time.format.DateTimeFormatter =
    java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ENGLISH)
        .withZone(java.time.ZoneId.of("Asia/Manila"))

class QrDisplayActivity : AppCompatActivity(), QrCredentialContract.View {
    private lateinit var presenter: QrCredentialPresenter
    private lateinit var qrImage: ImageView
    private lateinit var loadingText: TextView
    private lateinit var markDownloadedButton: Button
    private lateinit var downloadController: QrDownloadButtonController
    private lateinit var eventNameText: TextView
    private lateinit var attendeeNameText: TextView
    private lateinit var eventMetaText: TextView
    private lateinit var serialText: TextView
    private lateinit var savedCopyText: TextView
    private lateinit var emptyState: EventQrEmptyState
    private lateinit var errorStrip: View
    private var currentQrCredentialId: String? = null
    private var currentQrBitmap: Bitmap? = null
    private var currentEventTitle: String? = null
    private var currentRegistrationId: String? = null
    private var showingSavedCopy = false
    private var loadGeneration = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_display)

        presenter = QrCredentialPresenter(this, AttendeeRepository(this), UiStrings(this))
        qrImage = findViewById(R.id.qrImage)
        loadingText = findViewById(R.id.txtQrLoading)
        markDownloadedButton = findViewById(R.id.btnMarkQrDownloaded)
        downloadController = QrDownloadButtonController(
            markDownloadedButton,
            findViewById(R.id.progressQrDownload),
            findViewById(R.id.imgQrDownloadCheck)
        )
        eventNameText = findViewById(R.id.qrEventName)
        attendeeNameText = findViewById(R.id.qrAttendeeName)
        eventMetaText = findViewById(R.id.qrMeta)
        serialText = findViewById(R.id.qrSerial)
        savedCopyText = findViewById(R.id.txtQrSavedCopy)
        emptyState = findViewById(R.id.emptyQrPassState)
        errorStrip = findViewById(R.id.errorQrPass)
        // §6.8 row 22: a failed save re-runs the save. A pass that is not ready has nothing to save,
        // so Retry re-runs the load that produces the bitmap instead of re-reporting "still loading".
        errorStrip.findViewById<View>(R.id.btnErrorStripRetry).setOnClickListener {
            errorStrip.visibility = View.GONE
            if (currentQrBitmap == null) loadPass() else handleDownloadClick()
        }
        findViewById<TextView>(R.id.qrRole).text = getString(R.string.ui_attendee_attendee)

        findViewById<View>(R.id.nav_header_back).setOnClickListener { finish() }
        findViewById<View>(R.id.layoutQrPassContent).applyEventQrBottomInsetPadding()
        KeepScreenBright.bind(this, findViewById(R.id.switchKeepBright))

        loadPass()

        markDownloadedButton.setOnClickListener { handleDownloadClick() }
    }

    /**
     * Cache first, network second: the saved pass paints as soon as it loads, and the live
     * response replaces it when it lands. A refresh that fails leaves the saved pass untouched.
     */
    private fun loadPass() {
        emptyState.visibility = View.GONE
        val registrationId = intent.getStringExtra(EXTRA_REGISTRATION_ID).orEmpty()
        val qrCredentialId = intent.getStringExtra(EXTRA_QR_CREDENTIAL_ID).orEmpty()
        if (registrationId.isBlank() && qrCredentialId.isBlank()) return
        val generation = ++loadGeneration
        lifecycleScope.launch {
            val cached = withContext(Dispatchers.IO) {
                // Once per process, independent of the cancelled-only sweep: a revoked pass or a
                // finished event must not keep its QR value on disk. Runs before the read below.
                QrPassCache.sweepExpired(this@QrDisplayActivity)
                QrPassCache.load(this@QrDisplayActivity, cacheKey())
            }
            if (generation != loadGeneration) return@launch
            if (cached != null && !showingSavedCopy) bindSavedCopy(cached)
            presenter.load(registrationId, qrCredentialId.ifBlank { null })
        }
    }

    /** The id this screen opened with, so a credential-id-only open still gets an offline copy. */
    private fun cacheKey(): String =
        intent.getStringExtra(EXTRA_REGISTRATION_ID).orEmpty()
            .ifBlank { intent.getStringExtra(EXTRA_QR_CREDENTIAL_ID).orEmpty() }

    /**
     * Saves the QR bitmap off the main thread. While the save is in flight the
     * button shows an inline spinner and is disabled; once the image is written
     * a checkmark pulse plays, the confirmation toast fires, and the button
     * settles back to its idle state.
     */
    private fun handleDownloadClick() {
        if (downloadController.isSaving) return
        errorStrip.visibility = View.GONE
        val bitmap = currentQrBitmap
        if (bitmap != null) {
            val fileName = "EventQR_${currentEventTitle?.replace(" ", "_") ?: "Event"}_${currentRegistrationId ?: "ID"}"
            downloadController.showSaving()
            lifecycleScope.launch {
                val uri = withContext(Dispatchers.IO) {
                    BitmapSaver.saveBitmapToGallery(this@QrDisplayActivity, bitmap, fileName)
                }
                if (uri != null) {
                    currentQrCredentialId?.let { presenter.markDownloaded(it) }
                    downloadController.showSaved {
                        Toast.makeText(this@QrDisplayActivity, this@QrDisplayActivity.getString(R.string.attendee_qr_credential_qr_saved_to_gallery), Toast.LENGTH_LONG).show()
                    }
                } else {
                    downloadController.resetToIdle()
                    errorStrip.showInlineError(getString(R.string.ui_attendee_pass_save_failed))
                }
            }
        } else {
            errorStrip.showInlineError(getString(R.string.ui_attendee_pass_not_ready))
        }
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }

    override fun showLoading(isLoading: Boolean) {
        loadingText.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showLoadError(message: String) {
        // The saved pass is already on screen and says so; a failed refresh must not replace it.
        if (showingSavedCopy) return
        showUnavailable(R.string.ui_attendee_pass_error_title, message) { loadPass() }
    }

    override fun renderQr(snapshot: QrCredentialSnapshot, registration: RegistrationResponse?, eventTitle: String?) {
        if (!snapshot.active || snapshot.displayStatus == QrDisplayStatus.REVOKED) {
            clearCachedPass(snapshot.registrationId.toString())
            showUnavailable(
                R.string.ui_attendee_pass_revoked_title,
                getString(R.string.ui_attendee_pass_revoked_body),
            )
            return
        }

        // The pass prints the serial once, inside the credential (§5.14); the card that repeated
        // attendee, email and serial is gone.
        val serial = RegistrationNumberFormatter.format(registration?.registrationNumber) ?: "-"
        val name = eventTitle?.takeIf { it.isNotBlank() } ?: getString(R.string.common_event)
        val holderName = registration?.attendeeName.asCredentialHolderName()
        val meta = listOfNotNull(
            registration?.eventStartAt?.let { PASS_DATE_FORMATTER.format(it) },
            registration?.eventLocation?.takeIf { it.isNotBlank() },
        ).joinToString(" - ")

        // A blank value is the unavailable state, never a cached one: the encode would throw, and
        // the copy it used to write first made every later offline open crash again.
        if (!bindPass(snapshot.qrValue, name, serial, meta, attendeeName = holderName)) {
            if (!showingSavedCopy) {
                showUnavailable(
                    R.string.ui_attendee_pass_error_title,
                    getString(R.string.ui_attendee_pass_unreadable_body),
                ) { loadPass() }
            }
            return
        }

        currentQrCredentialId = snapshot.qrCredentialId.toString()
        currentRegistrationId = snapshot.registrationId.toString()
        currentEventTitle = eventTitle

        val eventEndAtMillis = registration?.eventEndAt?.toEpochMilli()
        // Without an end time there is no expiry, so an offline copy could outlive the event.
        if (eventEndAtMillis != null) {
            val pass = CachedQrPass(
                qrValue = snapshot.qrValue,
                eventName = name,
                serial = serial,
                meta = meta,
                eventEndAtMillis = eventEndAtMillis,
                savedAtMillis = System.currentTimeMillis(),
                attendeeName = holderName,
            )
            val key = cacheKey().ifBlank { snapshot.registrationId.toString() }
            lifecycleScope.launch(Dispatchers.IO) { QrPassCache.save(this@QrDisplayActivity, key, pass) }
        }
    }

    private fun bindSavedCopy(cached: CachedQrPass) {
        currentQrCredentialId = intent.getStringExtra(EXTRA_QR_CREDENTIAL_ID)?.takeIf { it.isNotBlank() }
        currentRegistrationId = intent.getStringExtra(EXTRA_REGISTRATION_ID)?.takeIf { it.isNotBlank() }
        currentEventTitle = cached.eventName
        if (!bindPass(cached.qrValue, cached.eventName, cached.serial, cached.meta, attendeeName = cached.attendeeName, savedCopy = true)) {
            // A stored value that cannot encode is worthless; drop it so the next open starts clean.
            clearCachedPass()
            return
        }
        savedCopyText.text = getString(
            R.string.ui_attendee_pass_last_updated,
            DateFormatters.displayFormatter.format(Instant.ofEpochMilli(cached.savedAtMillis)),
        )
        savedCopyText.visibility = View.VISIBLE
    }

    private fun bindPass(
        qrValue: String,
        eventName: String,
        serial: String,
        meta: String,
        attendeeName: String? = null,
        savedCopy: Boolean = false,
    ): Boolean {
        val bitmap = renderQrBitmap(qrValue) ?: return false
        currentQrBitmap = bitmap
        qrImage.setImageBitmap(bitmap)

        serialText.text = serial
        eventNameText.text = eventName
        val holder = attendeeName.asCredentialHolderName()
        attendeeNameText.text = holder.orEmpty()
        // No name is no line: never an empty 24dp slot, never the email, never "Guest".
        attendeeNameText.visibility = if (holder == null) View.GONE else View.VISIBLE
        eventMetaText.text = meta
        eventMetaText.visibility = if (meta.isBlank()) View.GONE else View.VISIBLE

        findViewById<View>(R.id.qrCredential).apply {
            visibility = View.VISIBLE
            contentDescription = getString(R.string.ui_attendee_pass_description, eventName, serial)
        }
        bindBadge(savedCopy)
        findViewById<View>(R.id.groupSavePass).visibility = View.VISIBLE
        findViewById<View>(R.id.rowKeepBright).visibility = View.VISIBLE
        savedCopyText.visibility = View.GONE
        emptyState.visibility = View.GONE
        emptyState.setAction(null)
        showingSavedCopy = savedCopy
        return true
    }

    /** Lime is confirmation only: a pass restored from disk is neutral, never "Valid". */
    private fun bindBadge(savedCopy: Boolean) {
        val badge = findViewById<TextView>(R.id.qrValidBadge)
        badge.setText(if (savedCopy) R.string.ui_credential_saved_copy else R.string.ui_credential_valid)
        badge.backgroundTintList = ColorStateList.valueOf(
            color(if (savedCopy) R.color.ui_surface_sunken else R.color.ui_accent),
        )
        badge.setTextColor(color(if (savedCopy) R.color.ui_text_secondary else R.color.ui_on_accent))
    }

    /** No live and no cached pass: the plate is gone, and one state names what to do next. */
    private fun showUnavailable(titleRes: Int, body: String, onRetry: (() -> Unit)? = null) {
        currentQrBitmap = null
        qrImage.setImageDrawable(null)
        findViewById<View>(R.id.qrCredential).visibility = View.GONE
        findViewById<View>(R.id.groupSavePass).visibility = View.GONE
        findViewById<View>(R.id.rowKeepBright).visibility = View.GONE
        savedCopyText.visibility = View.GONE
        showingSavedCopy = false
        emptyState.show(getString(titleRes), body)
        // Retry only where another attempt can change the answer; a revoked pass cannot.
        emptyState.setAction(onRetry?.let { getString(R.string.ui_attendee_retry) }, onRetry)
    }

    /** Drops the saved copy off the main thread, under this screen's key and [registrationId]. */
    private fun clearCachedPass(registrationId: String? = null) {
        val keys = listOf(cacheKey(), registrationId.orEmpty()).filter { it.isNotBlank() }.distinct()
        if (keys.isEmpty()) return
        lifecycleScope.launch(Dispatchers.IO) { keys.forEach { QrPassCache.clear(this@QrDisplayActivity, it) } }
    }

    private fun renderQrBitmap(value: String): Bitmap? {
        if (value.isBlank()) return null
        val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.Q)
        // A writer that rejects the payload must not take the screen down with it.
        val matrix = runCatching {
            QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 512, 512, hints)
        }.getOrNull() ?: return null
        val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
        val dark = color(R.color.qr_dark)
        val light = color(R.color.qr_light)
        for (x in 0 until matrix.width) {
            for (y in 0 until matrix.height) {
                bitmap.setPixel(x, y, if (matrix[x, y]) dark else light)
            }
        }
        return bitmap
    }
}
