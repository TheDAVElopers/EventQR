package com.thedavelopers.eventqr.features.staff.scanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Camera
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.features.scanpurposes.model.dto.ScanPurposeResponse
import com.thedavelopers.eventqr.features.staff.EventRegistrationsActivity
import com.thedavelopers.eventqr.features.staff.EventSpinnerOption
import com.thedavelopers.eventqr.features.staff.StaffDashboardActivity
import com.thedavelopers.eventqr.features.staff.StaffRepository
import com.thedavelopers.eventqr.features.staff.StaffScreenExtras
import com.thedavelopers.eventqr.features.staff.StaffTransactionsActivity
import com.thedavelopers.eventqr.features.staff.model.dto.ScanVerificationResponse
import com.thedavelopers.eventqr.features.staff.result.StaffScanResultActivity
import com.thedavelopers.eventqr.features.transactions.TransactionAdapter
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse
import org.json.JSONObject
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("DEPRECATION")
open class ScannerActivity : AppCompatActivity(), ScannerContract.View, SurfaceHolder.Callback, Camera.PreviewCallback {
    private lateinit var presenter: ScannerPresenter
    private lateinit var eventSpinner: Spinner
    private lateinit var purposeSpinner: Spinner
    private lateinit var qrInput: EditText
    private lateinit var notesInput: EditText
    private lateinit var resultText: TextView
    private lateinit var adapter: TransactionAdapter
    private lateinit var selectedEventTitle: TextView
    private lateinit var selectedEventDate: TextView
    private lateinit var selectedPurposeCard: LinearLayout
    private lateinit var selectedPurposeName: TextView
    private lateinit var selectedPurposePoints: TextView
    private lateinit var purposeChevron: TextView
    private lateinit var purposeDropdown: LinearLayout
    private lateinit var inlineCameraSurface: SurfaceView
    private lateinit var inlineCameraStatus: TextView
    private lateinit var scannerIcon: ImageView
    private var staffUserId: String? = null

    private val eventOptions = mutableListOf<EventSpinnerOption>()
    private val purposeOptions = mutableListOf<ScanPurposeResponse>()
    private var preselectedEventId: String? = null
    private var lastSubmittedSignature: String? = null
    private var lastSubmittedAtMs: Long = 0L
    private var submitInFlight: Boolean = false
    private var isPurposeDropdownOpen = false
    private var camera: Camera? = null
    private val decoding = AtomicBoolean(false)
    private val decoderExecutor = Executors.newSingleThreadExecutor()
    private val qrReader = MultiFormatReader()
    private val decodeHints = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.CHARACTER_SET to "UTF-8",
    )

    private val tag = "StaffQrScanner"
    private val duplicateWindowMs = 2_000L
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH)
    private val manilaZone: ZoneId = ZoneId.of("Asia/Manila")

    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Log.d(tag, "inline camera permission result granted=$granted")
        if (granted) {
            startInlineCameraIfReady()
        } else {
            inlineCameraStatus.text = "Camera permission is required for QR scanning"
            Toast.makeText(this, "Camera permission is required for QR scanning", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preselectedEventId = intent.getStringExtra(StaffScreenExtras.EXTRA_EVENT_ID)

        val sessionManager = SessionManager(this)
        if (RoleMapper.normalizeRole(sessionManager.getUserRole()) != AccountRole.STAFF.name) {
            Toast.makeText(this, "Access Denied: Staff only", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContentView(R.layout.activity_staff_scanner)

        presenter = ScannerPresenter(this, StaffRepository(this))
        eventSpinner = findViewById(R.id.spnScannerEvent)
        purposeSpinner = findViewById(R.id.spnScannerPurpose)
        qrInput = findViewById(R.id.edtScannerQr)
        notesInput = findViewById(R.id.edtScannerNotes)
        resultText = findViewById(R.id.txtScannerResult)
        selectedEventTitle = findViewById(R.id.txtScannerEventTitle)
        selectedEventDate = findViewById(R.id.txtScannerEventDate)
        selectedPurposeCard = findViewById(R.id.cardSelectedPurpose)
        selectedPurposeName = findViewById(R.id.txtSelectedPurposeName)
        selectedPurposePoints = findViewById(R.id.txtSelectedPurposePoints)
        purposeChevron = findViewById(R.id.txtPurposeChevron)
        purposeDropdown = findViewById(R.id.layoutPurposeDropdown)
        inlineCameraSurface = findViewById(R.id.surfaceInlineCameraPreview)
        inlineCameraStatus = findViewById(R.id.txtInlineCameraStatus)
        scannerIcon = findViewById(R.id.imgScannerIcon)
        adapter = TransactionAdapter()
        staffUserId = SessionManager(this).getUserId()

        inlineCameraSurface.holder.addCallback(this)
        inlineCameraSurface.setZOrderMediaOverlay(false)
        requestInlineCameraStart()

        findViewById<View>(R.id.navDashboard)?.setOnClickListener {
            startActivity(Intent(this, StaffDashboardActivity::class.java))
            finish()
        }

        findViewById<View>(R.id.navEvents)?.setOnClickListener {
            startActivity(Intent(this, EventRegistrationsActivity::class.java).apply {
                selectedEvent()?.id?.let { putExtra(StaffScreenExtras.EXTRA_EVENT_ID, it) }
            })
        }

        findViewById<View>(R.id.navLogs)?.setOnClickListener {
            startActivity(Intent(this, StaffTransactionsActivity::class.java).apply {
                selectedEvent()?.id?.let { putExtra(StaffScreenExtras.EXTRA_EVENT_ID, it) }
            })
        }

        selectedPurposeCard.setOnClickListener {
            setPurposeDropdownOpen(!isPurposeDropdownOpen)
        }

        findViewById<RecyclerView>(R.id.recyclerScannerResults).apply {
            layoutManager = LinearLayoutManager(this@ScannerActivity)
            adapter = this@ScannerActivity.adapter
        }

        findViewById<View>(R.id.layoutScannerPlaceholder)?.setOnClickListener {
            requestInlineCameraStart()
        }

        findViewById<Button>(R.id.btnSubmitScan).setOnClickListener {
            submitCurrentSelection(trigger = "manual")
        }

        presenter.loadEvents()
    }

    override fun onResume() {
        super.onResume()
        requestInlineCameraStart()
    }

    override fun onPause() {
        releaseInlineCamera()
        super.onPause()
    }

    override fun onDestroy() {
        presenter.detach()
        releaseInlineCamera()
        decoderExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        startInlineCameraIfReady()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        releaseInlineCamera()
        startInlineCameraIfReady()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        releaseInlineCamera()
    }

    override fun onPreviewFrame(data: ByteArray?, camera: Camera?) {
        if (data == null || camera == null || submitInFlight || !decoding.compareAndSet(false, true)) return
        val previewSize = camera.parameters.previewSize
        val width = previewSize.width
        val height = previewSize.height

        decoderExecutor.execute {
            val decoded = decodeFrame(data, width, height)
            runOnUiThread {
                if (!decoded.isNullOrBlank()) {
                    handleInlineQrValue(decoded)
                }
                decoding.set(false)
            }
        }
    }

    override fun showEvents(items: List<EventSpinnerOption>) {
        eventOptions.clear()
        eventOptions.addAll(items)
        val labels = items.map { it.label }
        eventSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        eventSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                bindSelectedEventHeader()
                loadSelectedPurposes()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        if (!preselectedEventId.isNullOrBlank()) {
            val index = items.indexOfFirst { it.id == preselectedEventId }
            if (index >= 0) eventSpinner.setSelection(index)
        }
        bindSelectedEventHeader()
        loadSelectedPurposes()
        findViewById<TextView>(R.id.txtScannerEmptyState).visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun showPurposes(items: List<ScanPurposeResponse>) {
        val activePurposes = items.filter { it.active }
        val selectedEventId = eventOptions.getOrNull(eventSpinner.selectedItemPosition)?.id
        val labels = activePurposes.map { it.name }
        Log.d(tag, "eventId=$selectedEventId loadedScanPurposeCount=${items.size} displayedOptionLabels=$labels")

        purposeOptions.clear()
        purposeOptions.addAll(activePurposes)

        if (activePurposes.isEmpty()) {
            purposeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("No scan purposes enabled for this event."))
            purposeSpinner.isEnabled = false
            selectedPurposeName.text = "No scan purposes enabled"
            selectedPurposePoints.text = "Configure scan purposes first"
            selectedPurposePoints.setTextColor(0xFF6B7280.toInt())
            purposeDropdown.removeAllViews()
            setPurposeDropdownOpen(false)
        } else {
            purposeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, activePurposes.map { it.name })
            purposeSpinner.isEnabled = true
            purposeSpinner.setSelection(0, false)
            bindSelectedPurposeHeader()
            renderPurposeDropdown()
        }
    }

    override fun appendScanResult(result: TransactionResponse) {
        adapter.submitItems(listOf(result))
    }

    override fun showVerificationResult(result: ScanVerificationResponse) {
        submitInFlight = false
        Log.d(tag, "backend verification result=SUCCESS eventId=${result.eventId} scanPurposeId=${result.scanPurposeId} message=${result.message}")
        resultText.text = result.message
        openVerificationResult(result)
    }

    override fun showScanError(message: String) {
        submitInFlight = false
        Log.w(tag, "backend verification result=ERROR message=$message")
        resultText.text = message
        showMessage(message)
        openRejectedResult(message)
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showLoading(isLoading: Boolean) {
        findViewById<View>(R.id.progressScanner).visibility = if (isLoading) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.btnSubmitScan).isEnabled = !isLoading
    }

    private fun requestInlineCameraStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startInlineCameraIfReady()
        } else {
            inlineCameraStatus.text = "Allow camera access to scan QR codes"
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startInlineCameraIfReady() {
        if (!::inlineCameraSurface.isInitialized || camera != null) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
        if (!inlineCameraSurface.holder.surface.isValid) return

        inlineCameraStatus.text = "Point camera at attendee QR code"
        runCatching {
            camera = Camera.open().apply {
                val params = parameters
                val focusModes = params.supportedFocusModes.orEmpty()
                when {
                    focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE) -> params.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
                    focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO) -> params.focusMode = Camera.Parameters.FOCUS_MODE_AUTO
                }
                params.previewFormat = android.graphics.ImageFormat.NV21
                this.parameters = params
                setPreviewDisplay(inlineCameraSurface.holder)
                setDisplayOrientation(90)
                setPreviewCallback(this@ScannerActivity)
                startPreview()
                scannerIcon.visibility = View.GONE
                Log.d(tag, "inline camera started preview=${params.previewSize.width}x${params.previewSize.height} focus=${params.focusMode}")
            }
        }.onFailure {
            inlineCameraStatus.text = "Unable to start camera"
            scannerIcon.visibility = View.VISIBLE
            Log.w(tag, "inline camera failed: ${it.message}", it)
        }
    }

    private fun releaseInlineCamera() {
        camera?.setPreviewCallback(null)
        runCatching { camera?.stopPreview() }
        camera?.release()
        camera = null
        if (::scannerIcon.isInitialized) scannerIcon.visibility = View.VISIBLE
    }

    private fun handleInlineQrValue(rawValue: String) {
        Log.d(tag, "inline raw QR value detected: $rawValue")
        val parsed = parseQrPayload(rawValue)
        if (parsed == null || parsed.qrValue.isBlank()) {
            showMessage("QR payload format is invalid.")
            Log.w(tag, "invalid inline QR payload raw=$rawValue")
            return
        }
        qrInput.setText(parsed.qrValue)
        qrInput.setSelection(parsed.qrValue.length)
        submitCurrentSelection(trigger = "inline-camera")
    }

    private fun decodeFrame(data: ByteArray, width: Int, height: Int): String? {
        val ySize = width * height
        if (data.size < ySize) return null
        val luma = data.copyOf(ySize)
        val rotated90 = rotateLuma90(luma, width, height)
        val rotated180 = rotateLuma90(rotated90, height, width)
        val rotated270 = rotateLuma90(rotated180, width, height)

        val candidates = listOf(
            Triple(luma, width, height),
            Triple(rotated90, height, width),
            Triple(rotated180, width, height),
            Triple(rotated270, height, width),
        )

        for ((buffer, w, h) in candidates) {
            val normal = decodeBinaryBitmap(buffer, w, h, invert = false)
            if (!normal.isNullOrBlank()) return normal
            val inverted = decodeBinaryBitmap(buffer, w, h, invert = true)
            if (!inverted.isNullOrBlank()) return inverted
        }
        return null
    }

    private fun decodeBinaryBitmap(data: ByteArray, width: Int, height: Int, invert: Boolean): String? {
        val source = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
        val bitmap = BinaryBitmap(HybridBinarizer(if (invert) source.invert() else source))
        return try {
            qrReader.setHints(decodeHints)
            qrReader.decodeWithState(bitmap).text
        } catch (_: NotFoundException) {
            null
        } catch (_: Exception) {
            null
        } finally {
            qrReader.reset()
        }
    }

    private fun rotateLuma90(input: ByteArray, width: Int, height: Int): ByteArray {
        val output = ByteArray(width * height)
        var index = 0
        for (x in 0 until width) {
            for (y in height - 1 downTo 0) {
                output[index++] = input[y * width + x]
            }
        }
        return output
    }

    private fun loadSelectedPurposes() {
        val selectedEvent = selectedEvent()
        if (selectedEvent != null) {
            presenter.loadPurposes(selectedEvent.id)
        }
    }

    private fun selectedEvent(): EventSpinnerOption? = eventOptions.getOrNull(eventSpinner.selectedItemPosition)

    private fun bindSelectedEventHeader() {
        val event = selectedEvent()
        selectedEventTitle.text = event?.label ?: "No assigned event"
        selectedEventDate.text = event?.eventStartAt?.atZone(manilaZone)?.format(dateFormatter).orEmpty()
    }

    private fun bindSelectedPurposeHeader() {
        val purpose = purposeOptions.getOrNull(purposeSpinner.selectedItemPosition)
        selectedPurposeName.text = purpose?.displayName().orEmpty().ifBlank { "Select purpose" }
        selectedPurposePoints.text = purpose?.pointsLabel().orEmpty()
        selectedPurposePoints.setTextColor(0xFF4F46E5.toInt())
    }

    private fun renderPurposeDropdown() {
        purposeDropdown.removeAllViews()
        purposeOptions.forEachIndexed { index, purpose ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(12), dp(16), dp(12))
                setBackgroundColor(if (index == purposeSpinner.selectedItemPosition) Color.parseColor("#EEF2FF") else Color.WHITE)
                setOnClickListener {
                    purposeSpinner.setSelection(index, false)
                    bindSelectedPurposeHeader()
                    renderPurposeDropdown()
                    setPurposeDropdownOpen(false)
                }
            }
            val textColumn = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            textColumn.addView(TextView(this).apply {
                text = purpose.displayName()
                setTextColor(if (index == purposeSpinner.selectedItemPosition) 0xFF4F46E5.toInt() else 0xFF111827.toInt())
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            textColumn.addView(TextView(this).apply {
                text = purpose.description?.takeIf { it.isNotBlank() } ?: purpose.defaultDescription()
                setTextColor(0xFF6B7280.toInt())
                textSize = 13f
            })
            row.addView(textColumn)
            row.addView(TextView(this).apply {
                text = purpose.pointsLabel()
                setTextColor(0xFF10B981.toInt())
                textSize = 13f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            purposeDropdown.addView(row)
        }
    }

    private fun setPurposeDropdownOpen(open: Boolean) {
        isPurposeDropdownOpen = open
        purposeDropdown.visibility = if (open) View.VISIBLE else View.GONE
        purposeChevron.text = if (open) "⌃" else "⌄"
    }

    private fun ScanPurposeResponse.displayName(): String = when (code) {
        ScanPurposeCode.ENTRY -> "Event Entry"
        ScanPurposeCode.ATTENDANCE -> "Session Attendance"
        ScanPurposeCode.BOOTH_VISIT -> "Booth Visit"
        ScanPurposeCode.BENEFIT_CLAIM -> "Benefit Claim"
        ScanPurposeCode.REWARD_REDEMPTION, ScanPurposeCode.REWARD_REDEMPTION_SCAN -> "Reward Redemption"
        ScanPurposeCode.EXIT -> "Event Exit"
        else -> name
    }

    private fun ScanPurposeResponse.defaultDescription(): String = when (code) {
        ScanPurposeCode.ENTRY -> "Record attendee entry"
        ScanPurposeCode.ATTENDANCE -> "Record session attendance"
        ScanPurposeCode.BOOTH_VISIT -> "Track booth/exhibitor visits"
        ScanPurposeCode.BENEFIT_CLAIM -> "Validate benefit/meal claims"
        ScanPurposeCode.REWARD_REDEMPTION, ScanPurposeCode.REWARD_REDEMPTION_SCAN -> "Process reward redemptions"
        ScanPurposeCode.EXIT -> "Record attendee exit"
        else -> "Scan attendee QR credential"
    }

    private fun ScanPurposeResponse.pointsLabel(): String = when (code) {
        ScanPurposeCode.ENTRY -> "+50 pts"
        ScanPurposeCode.ATTENDANCE -> "+30 pts"
        ScanPurposeCode.BOOTH_VISIT -> "+15 pts"
        ScanPurposeCode.EXIT -> "+25 pts"
        else -> ""
    }

    private fun submitCurrentSelection(trigger: String) {
        val selectedEvent = selectedEvent()
        if (selectedEvent == null) {
            showMessage("No assigned event selected.")
            Log.w(tag, "submit blocked: missing selected event")
            return
        }
        if (purposeOptions.isEmpty()) {
            showMessage("No scan purposes enabled for this event.")
            Log.w(tag, "submit blocked: no enabled scan purposes eventId=${selectedEvent.id}")
            return
        }
        val selectedPurpose = purposeOptions.getOrNull(purposeSpinner.selectedItemPosition)
        if (selectedPurpose == null) {
            showMessage("No scan purpose selected.")
            Log.w(tag, "submit blocked: missing selected scan purpose eventId=${selectedEvent.id}")
            return
        }

        val qrValue = qrInput.text.toString().trim()
        if (qrValue.isBlank()) {
            showMessage("QR payload format is invalid.")
            Log.w(tag, "submit blocked: empty QR value")
            return
        }

        val signature = "${selectedEvent.id}|${selectedPurpose.scanPurposeId}|$qrValue"
        val now = SystemClock.elapsedRealtime()
        if (submitInFlight || (signature == lastSubmittedSignature && now - lastSubmittedAtMs < duplicateWindowMs)) {
            showMessage("Scan is already being processed. Please wait.")
            Log.w(tag, "duplicate rapid submission prevented signature=$signature")
            return
        }

        submitInFlight = true
        lastSubmittedSignature = signature
        lastSubmittedAtMs = now
        Log.d(tag, "submit trigger=$trigger selectedEventId=${selectedEvent.id} selectedScanPurposeId=${selectedPurpose.scanPurposeId} selectedScanPurposeCode=${selectedPurpose.code} qrValue=$qrValue")
        presenter.submitScan(selectedEvent.id, selectedPurpose, qrValue, notesInput.text.toString(), staffUserId)
    }

    private fun openVerificationResult(result: ScanVerificationResponse) {
        val selectedPurpose = purposeOptions.getOrNull(purposeSpinner.selectedItemPosition)
        val selectedEvent = selectedEvent()
        startActivity(Intent(this, StaffScanResultActivity::class.java).apply {
            putExtra(StaffScreenExtras.EXTRA_IS_VALID, true)
            putExtra(StaffScreenExtras.EXTRA_MESSAGE, result.message.orEmpty())
            putExtra(StaffScreenExtras.EXTRA_EVENT_ID, result.eventId.toString())
            putExtra(StaffScreenExtras.EXTRA_EVENT_TITLE, selectedEvent?.label.orEmpty())
            putExtra(StaffScreenExtras.EXTRA_SCAN_PURPOSE_ID, result.scanPurposeId.toString())
            putExtra(StaffScreenExtras.EXTRA_SCAN_PURPOSE_NAME, selectedPurpose?.displayName() ?: result.scanPurposeCode.name)
            putExtra(StaffScreenExtras.EXTRA_SCAN_PURPOSE_CODE, result.scanPurposeCode.name)
            putExtra(StaffScreenExtras.EXTRA_QR_VALUE, result.qrValue)
            putExtra(StaffScreenExtras.EXTRA_STAFF_USER_ID, staffUserId.orEmpty())
            putExtra(StaffScreenExtras.EXTRA_ATTENDEE_ID, result.attendeeUserId.toString())
            putExtra(StaffScreenExtras.EXTRA_ATTENDEE_NAME, result.attendeeName.orEmpty())
            putExtra(StaffScreenExtras.EXTRA_ATTENDEE_EMAIL, result.attendeeEmail.orEmpty())
            putExtra(StaffScreenExtras.EXTRA_REGISTRATION_ID, result.registrationId.toString())
            putExtra(StaffScreenExtras.EXTRA_QR_CREDENTIAL_ID, result.qrCredentialId.toString())
            putExtra(StaffScreenExtras.EXTRA_REGISTRATION_STATUS, result.registrationStatus.name)
            putExtra(StaffScreenExtras.EXTRA_QR_ACTIVE, result.qrActive)
            putExtra(StaffScreenExtras.EXTRA_VERIFIED_AT, result.verifiedAt?.toString().orEmpty())
        })
    }

    private fun openRejectedResult(message: String) {
        val selectedPurpose = purposeOptions.getOrNull(purposeSpinner.selectedItemPosition)
        val selectedEvent = selectedEvent()
        startActivity(Intent(this, StaffScanResultActivity::class.java).apply {
            putExtra(StaffScreenExtras.EXTRA_IS_VALID, false)
            putExtra(StaffScreenExtras.EXTRA_MESSAGE, message)
            putExtra(StaffScreenExtras.EXTRA_EVENT_ID, selectedEvent?.id.orEmpty())
            putExtra(StaffScreenExtras.EXTRA_EVENT_TITLE, selectedEvent?.label.orEmpty())
            putExtra(StaffScreenExtras.EXTRA_SCAN_PURPOSE_ID, selectedPurpose?.scanPurposeId?.toString().orEmpty())
            putExtra(StaffScreenExtras.EXTRA_SCAN_PURPOSE_NAME, selectedPurpose?.displayName().orEmpty())
            putExtra(StaffScreenExtras.EXTRA_SCAN_PURPOSE_CODE, selectedPurpose?.code?.name.orEmpty())
            putExtra(StaffScreenExtras.EXTRA_QR_VALUE, qrInput.text.toString().trim())
            putExtra(StaffScreenExtras.EXTRA_STAFF_USER_ID, staffUserId.orEmpty())
        })
    }

    private fun parseQrPayload(raw: String): ParsedQrPayload? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        if (!(trimmed.startsWith("{") && trimmed.endsWith("}"))) {
            return ParsedQrPayload(trimmed, null)
        }

        return runCatching {
            val json = JSONObject(trimmed)
            val qrValue = firstNonBlank(json.optString("qrValue"), json.optString("qr_value"), json.optString("value"))
            val qrCredentialId = firstNonBlank(json.optString("qrCredentialId"), json.optString("qr_credential_id"), json.optString("credentialId"))
            qrValue?.let { ParsedQrPayload(it, qrCredentialId) }
        }.getOrNull()
    }

    private fun firstNonBlank(vararg values: String?): String? = values.firstOrNull { !it.isNullOrBlank() }?.trim()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class ParsedQrPayload(
        val qrValue: String,
        val qrCredentialId: String?,
    )
}
