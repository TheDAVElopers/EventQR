package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.core.util.UiStrings
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
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
import com.thedavelopers.eventqr.core.util.BitmapSaver
import com.thedavelopers.eventqr.features.qrcredential.model.dto.QrCredentialSnapshot
import com.thedavelopers.eventqr.features.registrations.RegistrationNumberFormatter
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse
import com.thedavelopers.eventqr.ui.theme.applyEventQrBottomInsetPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private lateinit var qrText: TextView
    private lateinit var loadingText: TextView
    private lateinit var markDownloadedButton: Button
    private lateinit var downloadController: QrDownloadButtonController
    private lateinit var attendeeNameText: TextView
    private lateinit var attendeeEmailText: TextView
    private lateinit var credentialIdText: TextView
    private lateinit var eventNameText: TextView
    private lateinit var eventMetaText: TextView
    private lateinit var serialText: TextView
    private var currentQrCredentialId: String? = null
    private var currentQrBitmap: Bitmap? = null
    private var currentEventTitle: String? = null
    private var currentRegistrationId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_display)

        presenter = QrCredentialPresenter(this, AttendeeRepository(this), UiStrings(this))
        qrImage = findViewById(R.id.qrImage)
        qrText = findViewById(R.id.txtQrValue)
        loadingText = findViewById(R.id.txtQrLoading)
        markDownloadedButton = findViewById(R.id.btnMarkQrDownloaded)
        downloadController = QrDownloadButtonController(
            markDownloadedButton,
            findViewById(R.id.progressQrDownload),
            findViewById(R.id.imgQrDownloadCheck)
        )
        attendeeNameText = findViewById(R.id.txtQrAttendeeName)
        attendeeEmailText = findViewById(R.id.txtQrAttendeeEmail)
        credentialIdText = findViewById(R.id.txtQrCredentialValue)
        eventNameText = findViewById(R.id.qrEventName)
        eventMetaText = findViewById(R.id.qrMeta)
        serialText = findViewById(R.id.qrSerial)
        findViewById<TextView>(R.id.qrRole).text = getString(R.string.ui_attendee_attendee)

        findViewById<View>(R.id.nav_header_back).setOnClickListener { finish() }
        findViewById<View>(R.id.layoutQrPassContent).applyEventQrBottomInsetPadding()
        KeepScreenBright.bind(this, findViewById(R.id.switchKeepBright))

        findViewById<Button>(R.id.btnLoadQr).setOnClickListener {
            presenter.load(
                findViewById<EditText>(R.id.edtQrRegistrationId).text.toString(),
                intent.getStringExtra(EXTRA_QR_CREDENTIAL_ID)
            )
        }

        val registrationId = intent.getStringExtra(EXTRA_REGISTRATION_ID).orEmpty()
        val qrCredentialId = intent.getStringExtra(EXTRA_QR_CREDENTIAL_ID).orEmpty()
        if (registrationId.isNotBlank()) {
            findViewById<EditText>(R.id.edtQrRegistrationId).setText(registrationId)
            presenter.load(registrationId, qrCredentialId.ifBlank { null })
        } else if (qrCredentialId.isNotBlank()) {
            presenter.load("", qrCredentialId)
        }

        markDownloadedButton.setOnClickListener { handleDownloadClick() }
    }

    /**
     * Saves the QR bitmap off the main thread. While the save is in flight the
     * button shows an inline spinner and is disabled; once the image is written
     * a checkmark pulse plays, the confirmation toast fires, and the button
     * settles back to its idle state.
     */
    private fun handleDownloadClick() {
        if (downloadController.isSaving) return
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
                    Toast.makeText(this@QrDisplayActivity, this@QrDisplayActivity.getString(R.string.attendee_qr_credential_failed_to_save_qr_image), Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(this, this.getString(R.string.attendee_qr_credential_qr_image_not_ready), Toast.LENGTH_SHORT).show()
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

    override fun renderQr(snapshot: QrCredentialSnapshot, registration: RegistrationResponse?, eventTitle: String?) {
        currentQrCredentialId = snapshot.qrCredentialId.toString()
        currentRegistrationId = snapshot.registrationId.toString()
        currentEventTitle = eventTitle

        qrText.text = snapshot.qrValue
        val bitmap = renderQrBitmap(snapshot.qrValue)
        currentQrBitmap = bitmap
        qrImage.setImageBitmap(bitmap)

        val serial = RegistrationNumberFormatter.format(registration?.registrationNumber) ?: "-"
        credentialIdText.text = serial
        serialText.text = serial
        attendeeNameText.text = registration?.attendeeName?.takeIf { it.isNotBlank() }
            ?: getString(R.string.ui_attendee_role)
        attendeeEmailText.text = registration?.attendeeEmail?.takeIf { it.isNotBlank() } ?: "-"
        eventNameText.text = eventTitle?.takeIf { it.isNotBlank() } ?: getString(R.string.common_event)

        val meta = listOfNotNull(
            registration?.eventStartAt?.let { PASS_DATE_FORMATTER.format(it) },
            registration?.eventLocation?.takeIf { it.isNotBlank() },
        ).joinToString(" - ")
        eventMetaText.text = meta
        eventMetaText.visibility = if (meta.isBlank()) View.GONE else View.VISIBLE

        findViewById<View>(R.id.qrCredential).contentDescription = getString(
            R.string.ui_attendee_pass_description,
            eventNameText.text,
            serial,
        )
    }

    private fun renderQrBitmap(value: String): Bitmap {
        val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.Q)
        val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 512, 512, hints)
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