package com.thedavelopers.eventqr.features.staff.scanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.hardware.Camera
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
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
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionScanResponse
import com.thedavelopers.eventqr.features.staff.EventSpinnerOption
import com.thedavelopers.eventqr.features.staff.StaffAssignedEventsActivity
import com.thedavelopers.eventqr.features.staff.StaffBottomNavItem
import com.thedavelopers.eventqr.features.staff.StaffDashboardActivity
import com.thedavelopers.eventqr.features.staff.StaffRepository
import com.thedavelopers.eventqr.features.staff.StaffScreenExtras
import com.thedavelopers.eventqr.features.staff.StaffTransactionsActivity
import com.thedavelopers.eventqr.features.staff.configureStaffBottomNav
import com.thedavelopers.eventqr.features.staff.model.dto.ScanVerificationResponse
import com.thedavelopers.eventqr.features.staff.reward.RewardRedemptionScanResultActivity
import com.thedavelopers.eventqr.features.rewards.AppRewardExtras
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
    private lateinit var selectedEventCard: LinearLayout
    private lateinit var eventChevron: TextView
    private lateinit var selectedPurposeCard: LinearLayout
    private lateinit var selectedPurposeName: TextView
    private lateinit var selectedPurposePoints: TextView
    private lateinit var purposeChevron: ImageView
    private lateinit var purposeDropdown: LinearLayout
    private lateinit var inlineCameraSurface: SurfaceView
    private lateinit var inlineCameraStatus: TextView
    private lateinit var scannerIcon: ImageView
    private lateinit var cameraContainer: FrameLayout
    private var staffUserId: String? = null

    private val eventOptions = mutableListOf<EventSpinnerOption>()
    private val purposeOptions = mutableListOf<ScanPurposeResponse>()
    private var preselectedEventId: String? = null
    private var lastSubmittedSignature: String? = null
    private var lastSubmittedAtMs: Long = 0L
    private var submitInFlight: Boolean = false
    private var isPurposeDropdownOpen = false
    private var isEventDropdownOpen = false
    private var purposePopup: PopupWindow? = null
    private var eventPopup: PopupWindow? = null
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
        if (!RoleMapper.isAtLeast(sessionManager.getUserRole(), AccountRole.STAFF)) {
            Toast.makeText(this, "Access Denied: Staff or above", Toast.LENGTH_LONG).show()
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
        selectedEventCard = findViewById(R.id.cardScannerEvent)
        selectedEventTitle = findViewById(R.id.txtScannerEventTitle)
        selectedEventDate = findViewById(R.id.txtScannerEventDate)
        eventChevron = findViewById(R.id.txtEventChevron)
        selectedPurposeCard = findViewById(R.id.cardSelectedPurpose)
        selectedPurposeName = findViewById(R.id.txtSelectedPurposeName)
        selectedPurposePoints = findViewById(R.id.txtSelectedPurposePoints)
        purposeChevron = findViewById(R.id.txtPurposeChevron)
        purposeDropdown = findViewById(R.id.layoutPurposeDropdown)
        inlineCameraSurface = findViewById(R.id.surfaceInlineCameraPreview)
        inlineCameraStatus = findViewById(R.id.txtInlineCameraStatus)
        scannerIcon = findViewById(R.id.imgScannerIcon)
        cameraContainer = findViewById(R.id.frameCameraContainer)
        adapter = TransactionAdapter()
        staffUserId = SessionManager(this).getUserId()

        normalizeChevron(eventChevron, false)
        normalizeChevron(purposeChevron, false)
        purposeDropdown.visibility = View.GONE
        inlineCameraSurface.holder.addCallback(this)
        inlineCameraSurface.setZOrderMediaOverlay(false)
        requestInlineCameraStart()

        selectedEventCard.setOnClickListener { setEventDropdownOpen(!isEventDropdownOpen) }
        selectedPurposeCard.setOnClickListener { setPurposeDropdownOpen(!isPurposeDropdownOpen) }

        configureStaffBottomNav(StaffBottomNavItem.SCANNER, selectedEvent()?.id)

        findViewById<RecyclerView>(R.id.recyclerScannerResults).apply {
            layoutManager = LinearLayoutManager(this@ScannerActivity)
            adapter = this@ScannerActivity.adapter
        }

        findViewById<View>(R.id.layoutScannerPlaceholder)?.setOnClickListener { requestInlineCameraStart() }
        findViewById<Button>(R.id.btnSubmitScan).setOnClickListener { submitCurrentSelection(trigger = "manual") }
        presenter.loadEvents()
    }

    override fun onResume() { super.onResume(); requestInlineCameraStart() }
    override fun onPause() { releaseInlineCamera(); eventPopup?.dismiss(); purposePopup?.dismiss(); super.onPause() }
    override fun onDestroy() { presenter.detach(); eventPopup?.dismiss(); purposePopup?.dismiss(); releaseInlineCamera(); decoderExecutor.shutdownNow(); super.onDestroy() }

    override fun surfaceCreated(holder: SurfaceHolder) { startInlineCameraIfReady() }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { releaseInlineCamera(); startInlineCameraIfReady() }
    override fun surfaceDestroyed(holder: SurfaceHolder) { releaseInlineCamera() }

    override fun onPreviewFrame(data: ByteArray?, camera: Camera?) {
        if (data == null || camera == null || submitInFlight || !decoding.compareAndSet(false, true)) return
        val previewSize = camera.parameters.previewSize
        decoderExecutor.execute {
            val decoded = decodeFrame(data, previewSize.width, previewSize.height)
            runOnUiThread {
                if (!decoded.isNullOrBlank()) handleInlineQrValue(decoded)
                decoding.set(false)
            }
        }
    }

    override fun showEvents(items: List<EventSpinnerOption>) {
        eventOptions.clear()
        eventOptions.addAll(items)
        eventSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items.map { it.label })
        eventSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                bindSelectedEventHeader()
                renderEventDropdown()
                loadSelectedPurposes()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        if (!preselectedEventId.isNullOrBlank()) {
            val index = items.indexOfFirst { it.id == preselectedEventId }
            if (index >= 0) eventSpinner.setSelection(index)
        }
        bindSelectedEventHeader()
        renderEventDropdown()
        loadSelectedPurposes()
        findViewById<TextView>(R.id.txtScannerEmptyState).visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun showPurposes(items: List<ScanPurposeResponse>) {
        val activePurposes = items.filter { it.active }
        purposeOptions.clear()
        purposeOptions.addAll(activePurposes)
        setPurposeDropdownOpen(false)
        if (activePurposes.isEmpty()) {
            purposeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("No scan purposes enabled for this event."))
            purposeSpinner.isEnabled = false
            selectedPurposeName.text = "No scan purposes enabled"
            selectedPurposePoints.text = "Configure scan purposes first"
            selectedPurposePoints.visibility = View.VISIBLE
            selectedPurposePoints.setTextColor(0xFF6B7280.toInt())
        } else {
            purposeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, activePurposes.map { it.name })
            purposeSpinner.isEnabled = true
            purposeSpinner.setSelection(0, false)
            bindSelectedPurposeHeader()
            renderPurposeDropdown()
        }
    }

    override fun appendScanResult(result: TransactionResponse) { adapter.submitItems(listOf(result)) }
    override fun showVerificationResult(result: ScanVerificationResponse) { submitInFlight = false; resultText.text = result.message; openVerificationResult(result) }
    override fun showRewardRedemptionScanResult(result: RewardRedemptionScanResponse) { submitInFlight = false; openRewardRedemptionScanResult(result) }
    override fun showScanError(message: String) { submitInFlight = false; resultText.text = message; showMessage(message); openRejectedResult(message) }
    override fun showMessage(message: String) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    override fun showLoading(isLoading: Boolean) { findViewById<Button>(R.id.btnSubmitScan).isEnabled = !isLoading }

    private fun requestInlineCameraStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startInlineCameraIfReady()
        else { inlineCameraStatus.text = "Allow camera access to scan QR codes"; cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }
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
                // Select optimal preview size to avoid default mismatch
                val containerW = cameraContainer.width
                val containerH = cameraContainer.height
                // Rotated 90°: target landscape dims = (containerH x containerW)
                val best = chooseOptimalPreviewSize(params.supportedPreviewSizes, containerH, containerW)
                if (best != null) {
                    params.setPreviewSize(best.width, best.height)
                }
                this.parameters = params
                setPreviewDisplay(inlineCameraSurface.holder)
                setDisplayOrientation(90)
                setPreviewCallback(this@ScannerActivity)
                startPreview()
                scannerIcon.visibility = View.GONE
                // Adjust SurfaceView to match preview aspect ratio (center-crop)
                val previewSize = parameters.previewSize
                applyCenterCrop(inlineCameraSurface, previewSize.height, previewSize.width, containerW, containerH)
            }
        }.onFailure {
            inlineCameraStatus.text = "Unable to start camera"
            scannerIcon.visibility = View.VISIBLE
            Log.w(tag, "inline camera failed: ${it.message}", it)
        }
    }

    /**
     * Pick the supported preview size closest to [targetW]×[targetH] (landscape).
     * Prefers sizes that are at least as large as the target for sharpness.
     */
    private fun chooseOptimalPreviewSize(
        sizes: List<Camera.Size>?,
        targetW: Int,
        targetH: Int
    ): Camera.Size? {
        if (sizes.isNullOrEmpty()) return null
        val targetRatio = targetW.toDouble() / targetH.coerceAtLeast(1)
        // Sort by how closely the aspect ratio matches, then prefer larger
        return sizes.minByOrNull {
            val ratio = it.width.toDouble() / it.height.coerceAtLeast(1)
            val ratioDiff = Math.abs(ratio - targetRatio)
            // Tie-break: prefer sizes >= target area
            val areaPenalty = if (it.width >= targetW && it.height >= targetH) 0.0 else 1000.0
            ratioDiff + areaPenalty
        }
    }

    /**
     * Resize [surface] so that a camera preview of [previewW]×[previewH] (portrait, post-rotation)
     * fills [containerW]×[containerH] without distortion (center-crop).
     */
    private fun applyCenterCrop(
        surface: SurfaceView,
        previewW: Int,
        previewH: Int,
        containerW: Int,
        containerH: Int
    ) {
        if (previewW <= 0 || previewH <= 0 || containerW <= 0 || containerH <= 0) return
        val previewAspect = previewW.toFloat() / previewH
        val containerAspect = containerW.toFloat() / containerH
        val (viewW, viewH) = if (previewAspect > containerAspect) {
            // Preview is wider proportionally → match height, overflow width
            ((containerH * previewAspect).toInt()) to containerH
        } else {
            // Preview is taller proportionally → match width, overflow height
            containerW to ((containerW / previewAspect).toInt())
        }
        surface.layoutParams = (surface.layoutParams as FrameLayout.LayoutParams).apply {
            width = viewW
            height = viewH
            gravity = Gravity.CENTER
        }
    }

    private fun releaseInlineCamera() { camera?.setPreviewCallback(null); runCatching { camera?.stopPreview() }; camera?.release(); camera = null; if (::scannerIcon.isInitialized) scannerIcon.visibility = View.VISIBLE }
    private fun handleInlineQrValue(rawValue: String) { val parsed = parseQrPayload(rawValue); if (parsed == null || parsed.qrValue.isBlank()) { showMessage("QR payload format is invalid."); return }; qrInput.setText(parsed.qrValue); qrInput.setSelection(parsed.qrValue.length); submitCurrentSelection(trigger = "inline-camera") }

    private fun decodeFrame(data: ByteArray, width: Int, height: Int): String? {
        val ySize = width * height
        if (data.size < ySize) return null
        val luma = data.copyOf(ySize)
        val rotated90 = rotateLuma90(luma, width, height)
        val rotated180 = rotateLuma90(rotated90, height, width)
        val rotated270 = rotateLuma90(rotated180, width, height)
        val candidates = listOf(Triple(luma, width, height), Triple(rotated90, height, width), Triple(rotated180, width, height), Triple(rotated270, height, width))
        for ((buffer, w, h) in candidates) {
            decodeBinaryBitmap(buffer, w, h, false)?.let { return it }
            decodeBinaryBitmap(buffer, w, h, true)?.let { return it }
        }
        return null
    }

    private fun decodeBinaryBitmap(data: ByteArray, width: Int, height: Int, invert: Boolean): String? {
        val source = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
        val bitmap = BinaryBitmap(HybridBinarizer(if (invert) source.invert() else source))
        return try { qrReader.setHints(decodeHints); qrReader.decodeWithState(bitmap).text } catch (_: NotFoundException) { null } catch (_: Exception) { null } finally { qrReader.reset() }
    }

    private fun rotateLuma90(input: ByteArray, width: Int, height: Int): ByteArray { val output = ByteArray(width * height); var index = 0; for (x in 0 until width) { for (y in height - 1 downTo 0) { output[index++] = input[y * width + x] } }; return output }
    private fun loadSelectedPurposes() { selectedEvent()?.let { presenter.loadPurposes(it.id) } }
    private fun selectedEvent(): EventSpinnerOption? = eventOptions.getOrNull(eventSpinner.selectedItemPosition)
    private fun bindSelectedEventHeader() { val event = selectedEvent(); selectedEventTitle.text = event?.label ?: "No assigned event"; selectedEventDate.text = event?.eventStartAt?.atZone(manilaZone)?.format(dateFormatter).orEmpty() }
    private fun bindSelectedPurposeHeader() {
        val purpose = purposeOptions.getOrNull(purposeSpinner.selectedItemPosition)
        if (purpose == null) {
            selectedPurposeName.text = "Select purpose"
            selectedPurposePoints.visibility = View.GONE
            return
        }
        selectedPurposeName.text = purpose.displayName()
        if (purpose.trackingOnly) {
            selectedPurposePoints.visibility = View.GONE
        } else {
            selectedPurposePoints.text = purpose.defaultDescription()
            selectedPurposePoints.setTextColor(0xFF6B7280.toInt())
            selectedPurposePoints.visibility = View.VISIBLE
        }
    }

    private fun renderEventDropdown() {
        eventPopup?.dismiss()
        eventPopup = PopupWindow(buildEventDropdownView(), selectedEventCard.width.takeIf { it > 0 } ?: ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            isOutsideTouchable = true
            elevation = dp(8).toFloat()
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setOnDismissListener { isEventDropdownOpen = false; normalizeChevron(eventChevron, false) }
        }
    }

    private fun buildEventDropdownView(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(R.drawable.bg_card)
        eventOptions.forEachIndexed { index, event ->
            addView(LinearLayout(this@ScannerActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(12), dp(16), dp(12))
                setBackgroundColor(if (index == eventSpinner.selectedItemPosition) Color.parseColor("#EEF2FF") else Color.WHITE)
                setOnClickListener {
                    eventSpinner.setSelection(index, false)
                    qrInput.setText("")
                    notesInput.setText("")
                    submitInFlight = false
                    lastSubmittedSignature = null
                    bindSelectedEventHeader()
                    renderEventDropdown()
                    loadSelectedPurposes()
                    setEventDropdownOpen(false)
                }
                addView(LinearLayout(this@ScannerActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    addView(TextView(this@ScannerActivity).apply {
                        text = event.label
                        setTextColor(if (index == eventSpinner.selectedItemPosition) 0xFF4F46E5.toInt() else 0xFF111827.toInt())
                        textSize = 14f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                    })
                    addView(TextView(this@ScannerActivity).apply {
                        text = event.eventStartAt?.atZone(manilaZone)?.format(dateFormatter).orEmpty().ifBlank { "Assigned event" }
                        setTextColor(0xFF6B7280.toInt())
                        textSize = 13f
                    })
                })
            })
        }
    }

    private fun setEventDropdownOpen(open: Boolean) {
        if (open && eventOptions.isEmpty()) return
        if (open) {
            setPurposeDropdownOpen(false)
            if (eventPopup == null || selectedEventCard.width > 0 && eventPopup?.width != selectedEventCard.width) renderEventDropdown()
            isEventDropdownOpen = true
            normalizeChevron(eventChevron, true)
            eventPopup?.showAsDropDown(selectedEventCard, 0, 0)
        } else {
            eventPopup?.dismiss()
            isEventDropdownOpen = false
            normalizeChevron(eventChevron, false)
        }
    }

    private fun renderPurposeDropdown() {
        purposeDropdown.visibility = View.GONE
        purposePopup?.dismiss()
        purposePopup = PopupWindow(buildPurposeDropdownView(), selectedPurposeCard.width.takeIf { it > 0 } ?: ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            isOutsideTouchable = true
            elevation = dp(8).toFloat()
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setOnDismissListener { isPurposeDropdownOpen = false; normalizeChevron(purposeChevron, false) }
        }
    }

    private fun buildPurposeDropdownView(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(R.drawable.bg_card)
        purposeOptions.forEachIndexed { index, purpose ->
            addView(LinearLayout(this@ScannerActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(12), dp(16), dp(12))
                setBackgroundColor(if (index == purposeSpinner.selectedItemPosition) Color.parseColor("#EEF2FF") else Color.WHITE)
                setOnClickListener { purposeSpinner.setSelection(index, false); bindSelectedPurposeHeader(); renderPurposeDropdown(); setPurposeDropdownOpen(false) }
                addView(LinearLayout(this@ScannerActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    addView(TextView(this@ScannerActivity).apply { text = purpose.displayName(); setTextColor(if (index == purposeSpinner.selectedItemPosition) 0xFF4F46E5.toInt() else 0xFF111827.toInt()); textSize = 14f; setTypeface(typeface, android.graphics.Typeface.BOLD) })
                    addView(TextView(this@ScannerActivity).apply { text = purpose.description?.takeIf { it.isNotBlank() } ?: purpose.defaultDescription(); setTextColor(0xFF6B7280.toInt()); textSize = 13f })
                })
            })
        }
    }

    private fun setPurposeDropdownOpen(open: Boolean) {
        if (open && purposeOptions.isEmpty()) return
        if (open) { setEventDropdownOpen(false); if (purposePopup == null || selectedPurposeCard.width > 0 && purposePopup?.width != selectedPurposeCard.width) renderPurposeDropdown(); isPurposeDropdownOpen = true; normalizeChevron(purposeChevron, true); purposePopup?.showAsDropDown(selectedPurposeCard, 0, 0) }
        else { purposePopup?.dismiss(); isPurposeDropdownOpen = false; normalizeChevron(purposeChevron, false) }
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

    private fun submitCurrentSelection(trigger: String) {
        val selectedEvent = selectedEvent() ?: return showMessage("No assigned event selected.")
        if (purposeOptions.isEmpty()) return showMessage("No scan purposes enabled for this event.")
        val selectedPurpose = purposeOptions.getOrNull(purposeSpinner.selectedItemPosition) ?: return showMessage("No scan purpose selected.")
        val qrValue = qrInput.text.toString().trim()
        if (qrValue.isBlank()) return showMessage("QR payload format is invalid.")
        val signature = "${selectedEvent.id}|${selectedPurpose.scanPurposeId}|$qrValue"
        val now = SystemClock.elapsedRealtime()
        if (submitInFlight || (signature == lastSubmittedSignature && now - lastSubmittedAtMs < duplicateWindowMs)) return showMessage("Scan is already being processed. Please wait.")
        submitInFlight = true
        lastSubmittedSignature = signature
        lastSubmittedAtMs = now
        if (selectedPurpose.code == ScanPurposeCode.REWARD_REDEMPTION_SCAN
            || selectedPurpose.code == ScanPurposeCode.REWARD_REDEMPTION) {
            presenter.submitRewardRedemptionScan(selectedEvent.id, selectedPurpose, qrValue, staffUserId)
            return
        }
        presenter.submitScan(selectedEvent.id, selectedPurpose, qrValue, notesInput.text.toString(), staffUserId)
    }

    private fun openRewardRedemptionScanResult(result: RewardRedemptionScanResponse) {
        startActivity(Intent(this, RewardRedemptionScanResultActivity::class.java).apply {
            putExtra(StaffScreenExtras.EXTRA_EVENT_ID, result.eventId.toString())
            putExtra(StaffScreenExtras.EXTRA_ATTENDEE_ID, result.attendeeUserId.toString())
            putExtra(StaffScreenExtras.EXTRA_ATTENDEE_NAME, result.attendeeName.orEmpty())
            putExtra(StaffScreenExtras.EXTRA_ATTENDEE_EMAIL, result.attendeeEmail.orEmpty())
            putExtra(StaffScreenExtras.EXTRA_SCAN_PURPOSE_ID, result.scanPurposeId.toString())
            putExtra(AppRewardExtras.EXTRA_REDEMPTION_SCAN_LOG_ID, result.redemptionScanLogId.toString())
            putExtra(AppRewardExtras.EXTRA_POINTS_BALANCE, result.pointsBalance)
            putExtra(StaffScreenExtras.EXTRA_STAFF_USER_ID, staffUserId.orEmpty())
            putExtra(StaffScreenExtras.EXTRA_SCAN_REJECTED, result.rejected)
            putExtra(StaffScreenExtras.EXTRA_SCAN_REJECTION_REASON, result.rejectionReason.orEmpty())
        })
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

    private fun normalizeChevron(view: View, open: Boolean) {
        when (view) {
            is TextView -> {
                view.includeFontPadding = false
                view.translationY = -dp(1).toFloat()
                view.text = if (open) "\u25B4" else "\u25BE"
            }
            is ImageView -> {
                view.rotation = if (open) 180f else 0f
            }
        }
    }

    private fun parseQrPayload(raw: String): ParsedQrPayload? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        if (!(trimmed.startsWith("{") && trimmed.endsWith("}"))) return ParsedQrPayload(trimmed, null)
        return runCatching {
            val json = JSONObject(trimmed)
            val qrValue = firstNonBlank(json.optString("qrValue"), json.optString("qr_value"), json.optString("value"))
            val qrCredentialId = firstNonBlank(json.optString("qrCredentialId"), json.optString("qr_credential_id"), json.optString("credentialId"))
            qrValue?.let { ParsedQrPayload(it, qrCredentialId) }
        }.getOrNull()
    }
    private fun firstNonBlank(vararg values: String?): String? = values.firstOrNull { !it.isNullOrBlank() }?.trim()
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private data class ParsedQrPayload(val qrValue: String, val qrCredentialId: String?)
}
