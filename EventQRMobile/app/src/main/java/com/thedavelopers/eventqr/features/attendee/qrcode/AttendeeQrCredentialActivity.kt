package com.thedavelopers.eventqr.features.attendee

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.util.BitmapSaver
import com.thedavelopers.eventqr.features.qrcredential.model.dto.QrCredentialSnapshot
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse

open class AttendeeQrCredentialActivity : AppCompatActivity(), QrCredentialContract.View {
    private lateinit var presenter: QrCredentialPresenter
    private lateinit var qrImage: ImageView
    private lateinit var qrText: TextView
    private lateinit var loadingText: TextView
    private lateinit var markDownloadedButton: Button
    private lateinit var attendeeNameText: TextView
    private lateinit var attendeeEmailText: TextView
    private lateinit var credentialIdText: TextView
    private lateinit var eventNameText: TextView
    private var currentQrCredentialId: String? = null
    private var currentQrBitmap: Bitmap? = null
    private var currentEventTitle: String? = null
    private var currentRegistrationId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_credential)

        presenter = QrCredentialPresenter(this, AttendeeRepository(this))
        qrImage = findViewById(R.id.imgQrCode)
        qrText = findViewById(R.id.txtQrValue)
        loadingText = findViewById(R.id.txtQrLoading)
        markDownloadedButton = findViewById(R.id.btnMarkQrDownloaded)
        attendeeNameText = findViewById(R.id.txtQrAttendeeName)
        attendeeEmailText = findViewById(R.id.txtQrAttendeeEmail)
        credentialIdText = findViewById(R.id.txtQrCredentialValue)
        eventNameText = findViewById(R.id.txtQrEventName)

        findViewById<View>(R.id.btnCloseQr)?.setOnClickListener { finish() }

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

        markDownloadedButton.setOnClickListener {
            val bitmap = currentQrBitmap
            if (bitmap != null) {
                val fileName = "EventQR_${currentEventTitle?.replace(" ", "_") ?: "Event"}_${currentRegistrationId ?: "ID"}"
                val uri = BitmapSaver.saveBitmapToGallery(this, bitmap, fileName)

                if (uri != null) {
                    currentQrCredentialId?.let { presenter.markDownloaded(it) }
                    Toast.makeText(this, "QR saved to gallery", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Failed to save QR image", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "QR image not ready", Toast.LENGTH_SHORT).show()
            }
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

        credentialIdText.text = "QR-${snapshot.eventId.toString().take(4).uppercase()}-${snapshot.qrCredentialId.toString().take(8).uppercase()}"
        attendeeNameText.text = registration?.attendeeName ?: "Attendee"
        attendeeEmailText.text = registration?.attendeeEmail ?: "-"
        eventNameText.text = eventTitle ?: "Event"
    }

    private fun renderQrBitmap(value: String): Bitmap {
        val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 512, 512)
        val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
        for (x in 0 until matrix.width) {
            for (y in 0 until matrix.height) {
                bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }
}
