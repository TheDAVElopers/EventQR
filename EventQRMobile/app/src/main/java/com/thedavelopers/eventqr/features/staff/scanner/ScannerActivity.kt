package com.thedavelopers.eventqr.features.staff.scanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.lifecycle.lifecycleScope
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
import com.thedavelopers.eventqr.core.util.UiStrings
import com.thedavelopers.eventqr.features.idprinting.PrintPermission
import com.thedavelopers.eventqr.features.rewards.AppRewardExtras
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionScanResponse
import com.thedavelopers.eventqr.features.scanpurposes.model.dto.ScanPurposeResponse
import com.thedavelopers.eventqr.features.staff.EventSpinnerOption
import com.thedavelopers.eventqr.features.staff.StaffAssignedEventsActivity
import com.thedavelopers.eventqr.features.staff.StaffBottomNavItem
import com.thedavelopers.eventqr.features.staff.StaffRepository
import com.thedavelopers.eventqr.features.staff.StaffScreenExtras
import com.thedavelopers.eventqr.features.staff.StaffTransactionsActivity
import com.thedavelopers.eventqr.features.staff.configureStaffBottomNav
import com.thedavelopers.eventqr.features.staff.model.dto.ScanVerificationResponse
import com.thedavelopers.eventqr.features.staff.result.StaffScanResultActivity
import com.thedavelopers.eventqr.features.staff.reward.RewardRedemptionScanResultActivity
import com.thedavelopers.eventqr.features.transactions.TransactionAdapter
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse
import com.thedavelopers.eventqr.ui.components.PassHeadBinder
import com.thedavelopers.eventqr.ui.theme.applyEventQrSystemBarAppearance
import com.thedavelopers.eventqr.ui.theme.applyEventQrTopInsetPadding
import com.thedavelopers.eventqr.ui.theme.color
import com.thedavelopers.eventqr.ui.theme.dimenOffset
import org.json.JSONObject
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Staff scanner (§5.13). The camera is the only large dark surface in the app and it is flat:
 * include_scan_viewport owns the panel, the four 24dp brackets, the torch and the one guidance
 * line. This class owns everything that can break - permission flow, camera lifecycle, ZXing
 * decode, event/purpose selection, duplicate suppression and result routing - and keeps the
 * screen free of toasts for flow-critical outcomes (failures land in the inline strip).
 */
@Suppress("DEPRECATION")
open class ScannerActivity : AppCompatActivity(), ScannerContract.View, TextureView.SurfaceTextureListener, Camera.PreviewCallback {
    private lateinit var presenter: ScannerPresenter
    private lateinit var eventSpinner: Spinner
    private lateinit var purposeSpinner: Spinner
    private lateinit var qrInput: EditText
    private lateinit var notesInput: EditText
    private lateinit var resultStrip: TextView
    private lateinit var adapter: TransactionAdapter
    private lateinit var contextBar: View
    private lateinit var contextEvent: TextView
    private lateinit var contextPurpose: TextView
    private lateinit var purposeNote: TextView
    private lateinit var purposeDropdown: LinearLayout
    private lateinit var emptyEvents: View
    private lateinit var inlineCameraPreview: TextureView
    private lateinit var cameraStatus: TextView
    private lateinit var scannerIcon: ImageView
    private lateinit var cameraContainer: FrameLayout
    private lateinit var scanPanel: View
    private lateinit var torchButton: ImageView
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
    private var torchOn = false
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

    // The system permission dialog is requested automatically at most once per screen open; after
    // that only a tap on the camera panel asks again, so onResume never loops the prompt.
    private var cameraPermissionAutoRequested = false
    private var cameraPermissionDenied = false
    private var cameraPermissionDialog: AlertDialog? = null

    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Log.d(tag, "inline camera permission result granted=$granted")
        if (granted) {
            cameraPermissionDenied = false
            startInlineCameraIfReady()
        } else {
            cameraPermissionDenied = true
            cameraStatus.text = getString(R.string.scanner_camera_permission_tap_to_enable)
            if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                Toast.makeText(this, this.getString(R.string.scanner_camera_permission_is_required_for_qr), Toast.LENGTH_LONG).show()
            } else {
                // No rationale right after a denial means the system will not show its dialog again,
                // so the only way back is the app's settings page.
                showCameraSettingsDialog()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preselectedEventId = intent.getStringExtra(StaffScreenExtras.EXTRA_EVENT_ID)
        cameraPermissionAutoRequested = savedInstanceState?.getBoolean(STATE_CAMERA_PERMISSION_REQUESTED) ?: false
        cameraPermissionDenied = savedInstanceState?.getBoolean(STATE_CAMERA_PERMISSION_DENIED) ?: false

        val sessionManager = SessionManager(this)
        if (!RoleMapper.isAtLeast(sessionManager.getUserRole(), AccountRole.STAFF)) {
            Toast.makeText(this, this.getString(R.string.staff_dashboard_access_denied_staff_or_above), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContentView(R.layout.activity_staff_scanner)
        applyEventQrSystemBarAppearance(lightStatusBars = false)
        findViewById<View>(R.id.passHeadIdentity).applyEventQrTopInsetPadding()
        PassHeadBinder.bindCompact(
            findViewById(R.id.passHead),
            title = getString(R.string.ui_staff_scan),
            eyebrow = getString(R.string.ui_staff_role),
        )

        presenter = ScannerPresenter(this, StaffRepository(this), UiStrings(this))
        eventSpinner = findViewById(R.id.spnScannerEvent)
        purposeSpinner = findViewById(R.id.spnScannerPurpose)
        qrInput = findViewById(R.id.edtScannerQr)
        notesInput = findViewById(R.id.edtScannerNotes)
        resultStrip = findViewById(R.id.txtScannerResult)
        contextBar = findViewById(R.id.scanContext)
        contextEvent = findViewById(R.id.scanContextEvent)
        contextPurpose = findViewById(R.id.scanContextPurpose)
        purposeNote = findViewById(R.id.txtSelectedPurposePoints)
        purposeDropdown = findViewById(R.id.layoutPurposeDropdown)
        emptyEvents = findViewById(R.id.txtScannerEmptyState)
        cameraStatus = findViewById(R.id.scanGuidance)
        scanPanel = findViewById(R.id.scanPanel)
        torchButton = findViewById(R.id.scanTorch)
        adapter = TransactionAdapter()
        staffUserId = sessionManager.getUserId()

        // The shared viewport's preview slot gets the camera surface, behind the frame + brackets.
        val previewSlot = findViewById<FrameLayout>(R.id.scanPreview)
        val preview = layoutInflater.inflate(R.layout.ui_staff_scan_preview, previewSlot, false)
        previewSlot.addView(preview, 0)
        inlineCameraPreview = preview.findViewById(R.id.textureInlineCameraPreview)
        scannerIcon = preview.findViewById(R.id.imgScannerIcon)
        cameraContainer = preview.findViewById(R.id.frameCameraContainer)

        purposeDropdown.visibility = View.GONE
        resultStrip.visibility = View.GONE
        inlineCameraPreview.surfaceTextureListener = this

        contextEvent.setOnClickListener { setEventDropdownOpen(!isEventDropdownOpen) }
        contextPurpose.setOnClickListener { setPurposeDropdownOpen(!isPurposeDropdownOpen) }
        torchButton.setOnClickListener { toggleTorch() }

        configureStaffBottomNav(StaffBottomNavItem.SCANNER, selectedEvent()?.id)

        findViewById<RecyclerView>(R.id.recyclerScannerResults).apply {
            layoutManager = LinearLayoutManager(this@ScannerActivity)
            adapter = this@ScannerActivity.adapter
        }

        scanPanel.setOnClickListener { requestInlineCameraStart(userInitiated = true) }
        findViewById<View>(R.id.btnSubmitScan).setOnClickListener { submitCurrentSelection(trigger = "manual") }
        presenter.loadEvents()
    }

    override fun onResume() { super.onResume(); requestInlineCameraStart(userInitiated = false) }
    override fun onPause() { releaseInlineCamera(); eventPopup?.dismiss(); purposePopup?.dismiss(); super.onPause() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_CAMERA_PERMISSION_REQUESTED, cameraPermissionAutoRequested)
        outState.putBoolean(STATE_CAMERA_PERMISSION_DENIED, cameraPermissionDenied)
    }
    override fun onDestroy() { cameraPermissionDialog?.dismiss(); presenter.detach(); eventPopup?.dismiss(); purposePopup?.dismiss(); releaseInlineCamera(); decoderExecutor.shutdownNow(); super.onDestroy() }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        startInlineCameraIfReady()
    }

    /** A re-measure (rotation, insets) only needs the crop re-fitted; restarting here would restart the camera. */
    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        runCatching { camera?.parameters?.previewSize }.getOrNull()?.let {
            applyCenterCrop(inlineCameraPreview, it.height, it.width)
        }
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        releaseInlineCamera()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

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

    // ── Contract ─────────────────────────────────────────────────────────────

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
        emptyEvents.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun showPurposes(items: List<ScanPurposeResponse>) {
        // ID printing is only offered while the caller holds the Print IDs permission for the event.
        val activePurposes = items.filter { it.active && isPurposeAllowed(it) }
        purposeOptions.clear()
        purposeOptions.addAll(activePurposes)
        setPurposeDropdownOpen(false)
        if (activePurposes.isEmpty()) {
            purposeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf(getString(R.string.scanner_no_scan_purposes_enabled_for_this_ev)))
            purposeSpinner.isEnabled = false
            contextPurpose.text = getString(R.string.scanner_no_scan_purposes_enabled)
            purposeNote.text = getString(
                if (items.any { it.active && it.code == ScanPurposeCode.ID_PRINT }) {
                    R.string.staff_print_id_permission_required
                } else {
                    R.string.scanner_configure_scan_purposes_first
                },
            )
            purposeNote.visibility = View.VISIBLE
        } else {
            purposeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, activePurposes.map { it.name })
            purposeSpinner.isEnabled = true
            purposeSpinner.setSelection(0, false)
            bindSelectedPurposeHeader()
            renderPurposeDropdown()
        }
    }

    override fun appendScanResult(result: TransactionResponse) { adapter.submitItems(listOf(result)) }

    override fun showVerificationResult(result: ScanVerificationResponse) {
        submitInFlight = false
        resultStrip.visibility = View.GONE
        openVerificationResult(result)
    }

    override fun showRewardRedemptionScanResult(result: RewardRedemptionScanResponse) {
        submitInFlight = false
        resultStrip.visibility = View.GONE
        openRewardRedemptionScanResult(result)
    }

    override fun showScanError(message: String) {
        submitInFlight = false
        resetBracketTint()
        // Flow-critical failure: an inline strip, never a toast (§5.13, §10.15).
        resultStrip.text = message
        resultStrip.visibility = View.VISIBLE
        openRejectedResult(message)
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showLoading(isLoading: Boolean) {
        findViewById<View>(R.id.btnSubmitScan).isEnabled = !isLoading
    }

    // ── Camera ───────────────────────────────────────────────────────────────

    private fun requestInlineCameraStart(userInitiated: Boolean) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraPermissionDenied = false
            startInlineCameraIfReady()
            return
        }
        if (!userInitiated && cameraPermissionAutoRequested) {
            cameraStatus.text = getString(R.string.scanner_camera_permission_tap_to_enable)
            return
        }
        cameraPermissionAutoRequested = true
        when {
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> showCameraRationaleDialog()
            cameraPermissionDenied -> showCameraSettingsDialog()
            else -> launchCameraPermissionRequest()
        }
    }

    private fun launchCameraPermissionRequest() {
        cameraStatus.text = getString(R.string.scanner_allow_camera_access_to_scan_qr_codes)
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun showCameraRationaleDialog() {
        if (cameraPermissionDialog?.isShowing == true) return
        cameraPermissionDialog = AlertDialog.Builder(this)
            .setTitle(R.string.scanner_camera_permission_title)
            .setMessage(R.string.scanner_camera_permission_rationale)
            .setPositiveButton(R.string.scanner_camera_permission_allow) { _, _ -> launchCameraPermissionRequest() }
            .setNegativeButton(R.string.scanner_camera_permission_not_now) { _, _ ->
                cameraStatus.text = getString(R.string.scanner_camera_permission_tap_to_enable)
            }
            .show()
    }

    private fun showCameraSettingsDialog() {
        if (cameraPermissionDialog?.isShowing == true) return
        cameraPermissionDialog = AlertDialog.Builder(this)
            .setTitle(R.string.scanner_camera_permission_title)
            .setMessage(R.string.scanner_camera_permission_denied_settings)
            .setPositiveButton(R.string.scanner_camera_permission_open_settings) { _, _ -> openAppSettings() }
            .setNegativeButton(R.string.scanner_camera_permission_not_now, null)
            .show()
    }

    private fun openAppSettings() {
        val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        runCatching { startActivity(settingsIntent) }.onFailure {
            Toast.makeText(this, getString(R.string.scanner_camera_permission_is_required_for_qr), Toast.LENGTH_LONG).show()
        }
    }

    private fun startInlineCameraIfReady() {
        if (!::inlineCameraPreview.isInitialized || camera != null) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
        if (!inlineCameraPreview.isAvailable) return
        cameraStatus.text = getString(R.string.ui_scan_guidance)
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
                setPreviewTexture(inlineCameraPreview.surfaceTexture)
                setDisplayOrientation(90)
                setPreviewCallback(this@ScannerActivity)
                startPreview()
                scannerIcon.visibility = View.GONE
                // Fill the 240dp square with the preview, keeping its aspect ratio (centre-crop)
                val previewSize = parameters.previewSize
                applyCenterCrop(inlineCameraPreview, previewSize.height, previewSize.width)
            }
            torchButton.isEnabled = camera?.parameters?.supportedFlashModes?.contains(Camera.Parameters.FLASH_MODE_TORCH) == true
            updateTorchAffordance()
        }.onFailure {
            cameraStatus.text = getString(R.string.scanner_unable_to_start_camera)
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
        targetH: Int,
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
     * Fill the square preview with a camera frame of [previewW]×[previewH] (portrait, post-rotation)
     * without distortion, by scaling the buffer up inside the view and letting the view clip the
     * overhang. A TextureView draws its transformed texture inside its own bounds, so nothing of the
     * preview can land outside the bracket square - which a SurfaceView cannot guarantee below API 30.
     */
    private fun applyCenterCrop(preview: TextureView, previewW: Int, previewH: Int) {
        val viewW = preview.width.toFloat()
        val viewH = preview.height.toFloat()
        if (previewW <= 0 || previewH <= 0 || viewW <= 0f || viewH <= 0f) return
        // A TextureView stretches the buffer to its bounds; scale that mapping up to a cover-crop.
        val coverScale = maxOf(viewW / previewW, viewH / previewH)
        val matrix = Matrix()
        matrix.setScale(
            coverScale * previewW / viewW,
            coverScale * previewH / viewH,
            viewW / 2f,
            viewH / 2f,
        )
        preview.setTransform(matrix)
    }

    private fun releaseInlineCamera() {
        camera?.setPreviewCallback(null)
        runCatching { camera?.stopPreview() }
        camera?.release()
        camera = null
        torchOn = false
        if (::scannerIcon.isInitialized) scannerIcon.visibility = View.VISIBLE
        if (::torchButton.isInitialized) {
            torchButton.isEnabled = false
            updateTorchAffordance()
        }
    }

    /** §5.13: the torch is a 44dp target inside the panel and it must actually toggle. */
    private fun toggleTorch() {
        val cam = camera ?: return
        val supported = cam.parameters.supportedFlashModes.orEmpty()
        if (!supported.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
            showMessage(getString(R.string.staff_torch_unavailable))
            return
        }
        runCatching {
            val params = cam.parameters
            torchOn = !torchOn
            params.flashMode = if (torchOn) Camera.Parameters.FLASH_MODE_TORCH else Camera.Parameters.FLASH_MODE_OFF
            cam.parameters = params
        }.onFailure { Log.w(tag, "torch toggle failed: ${it.message}", it) }
        updateTorchAffordance()
    }

    private fun updateTorchAffordance() {
        // The torch lives on the light guidance row, so the ink token is the readable one; lime
        // full-strength would vanish on ui_background - ui_accent_deep is the accent for light.
        ImageViewCompat.setImageTintList(
            torchButton,
            ColorStateList.valueOf(color(if (torchOn) R.color.ui_accent_deep else R.color.ui_text_primary)),
        )
        torchButton.contentDescription =
            getString(if (torchOn) R.string.staff_torch_on else R.string.staff_torch_off)
    }

    // ── Decode ───────────────────────────────────────────────────────────────

    private fun handleInlineQrValue(rawValue: String) {
        val parsed = parseQrPayload(rawValue)
        if (parsed == null || parsed.qrValue.isBlank()) {
            showScanError(getString(R.string.scanner_qr_payload_format_is_invalid))
            return
        }
        flashBrackets()
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
        val candidates = listOf(Triple(luma, width, height), Triple(rotated90, height, width), Triple(rotated180, width, height), Triple(rotated270, width, height))
        for ((buffer, w, h) in candidates) {
            decodeBinaryBitmap(buffer, w, h, false)?.let { return it }
            decodeBinaryBitmap(buffer, w, h, true)?.let { return it }
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

    private fun flashBrackets() {
        setBracketTint(R.color.ui_accent)
        scanPanel.postDelayed({ resetBracketTint() }, BRACKET_FLASH_MS)
    }

    private fun resetBracketTint() = setBracketTint(R.color.ui_scan_bracket)

    private fun setBracketTint(colorRes: Int) {
        val tint = ColorStateList.valueOf(color(colorRes))
        BRACKET_IDS.toList().mapNotNull { findViewById<ImageView>(it) }
            .forEach { ImageViewCompat.setImageTintList(it, tint) }
    }

    // ── Selection ────────────────────────────────────────────────────────────

    private fun loadSelectedPurposes() {
        selectedEvent()?.let { presenter.loadPurposes(it.id) }
    }

    private fun selectedEvent(): EventSpinnerOption? = eventOptions.getOrNull(eventSpinner.selectedItemPosition)

    /**
     * Every purpose is offered except ID printing, which needs the caller's Print IDs permission for
     * the selected event (an event with no permission flag of its own - the unassigned caller,
     * organizer or ADMIN - keeps it available and the server decides).
     */
    private fun isPurposeAllowed(purpose: ScanPurposeResponse): Boolean =
        purpose.code != ScanPurposeCode.ID_PRINT || PrintPermission.isPrintAllowed(selectedEvent()?.canPrintId)

    private fun bindSelectedEventHeader() {
        val event = selectedEvent()
        contextEvent.text = event?.label ?: getString(R.string.scanner_no_assigned_event)
    }

    private fun bindSelectedPurposeHeader() {
        val purpose = purposeOptions.getOrNull(purposeSpinner.selectedItemPosition)
        if (purpose == null) {
            contextPurpose.text = getString(R.string.staff_scanner_select_purpose)
            purposeNote.visibility = View.GONE
            return
        }
        contextPurpose.text = purpose.displayName()
        if (purpose.trackingOnly) {
            purposeNote.visibility = View.GONE
        } else {
            purposeNote.text = purpose.defaultDescription()
            purposeNote.visibility = View.VISIBLE
        }
    }

    // ── Dropdowns (shared surface, no card stack) ────────────────────────────

    private fun renderEventDropdown() {
        eventPopup?.dismiss()
        eventPopup = PopupWindow(
            buildEventDropdownView(),
            contextBar.width.takeIf { it > 0 } ?: ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true,
        ).apply {
            isOutsideTouchable = true
            elevation = dimenOffset(R.dimen.ui_sheet_elevation).toFloat()
            setBackgroundDrawable(ColorDrawable(color(R.color.ui_surface)))
            setOnDismissListener { isEventDropdownOpen = false }
        }
    }

    private fun buildEventDropdownView(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(R.drawable.bg_ui_card)
        eventOptions.forEachIndexed { index, event ->
            val selected = index == eventSpinner.selectedItemPosition
            addView(LinearLayout(this@ScannerActivity).apply {
                orientation = LinearLayout.VERTICAL
                minimumHeight = dimenOffset(R.dimen.ui_touch_min)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(
                    dimenOffset(R.dimen.ui_gutter),
                    dimenOffset(R.dimen.space_md),
                    dimenOffset(R.dimen.ui_gutter),
                    dimenOffset(R.dimen.space_md),
                )
                setBackgroundColor(color(if (selected) R.color.ui_primary_wash else R.color.ui_surface))
                setOnClickListener {
                    eventSpinner.setSelection(index, false)
                    qrInput.setText("")
                    notesInput.setText("")
                    submitInFlight = false
                    lastSubmittedSignature = null
                    resultStrip.visibility = View.GONE
                    resetBracketTint()
                    bindSelectedEventHeader()
                    renderEventDropdown()
                    loadSelectedPurposes()
                    setEventDropdownOpen(false)
                }
                addView(TextView(this@ScannerActivity).apply {
                    text = event.label
                    setTextAppearance(R.style.TextAppearance_EventQR_Type_Title)
                    setTextColor(color(R.color.ui_text_primary))
                })
                addView(TextView(this@ScannerActivity).apply {
                    text = event.eventStartAt?.atZone(manilaZone)?.format(dateFormatter).orEmpty()
                        .ifBlank { getString(R.string.scanner_assigned_event_fallback) }
                    setTextAppearance(R.style.TextAppearance_EventQR_Type_BodySmall)
                    setTextColor(color(R.color.ui_text_secondary))
                })
            })
        }
    }

    private fun setEventDropdownOpen(open: Boolean) {
        if (open && eventOptions.isEmpty()) return
        if (open) {
            setPurposeDropdownOpen(false)
            if (eventPopup == null || contextBar.width > 0 && eventPopup?.width != contextBar.width) renderEventDropdown()
            isEventDropdownOpen = true
            eventPopup?.showAsDropDown(contextBar, 0, 0)
        } else {
            eventPopup?.dismiss()
            isEventDropdownOpen = false
        }
    }

    private fun renderPurposeDropdown() {
        purposeDropdown.visibility = View.GONE
        purposePopup?.dismiss()
        purposePopup = PopupWindow(
            buildPurposeDropdownView(),
            contextBar.width.takeIf { it > 0 } ?: ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true,
        ).apply {
            isOutsideTouchable = true
            elevation = dimenOffset(R.dimen.ui_sheet_elevation).toFloat()
            setBackgroundDrawable(ColorDrawable(color(R.color.ui_surface)))
            setOnDismissListener { isPurposeDropdownOpen = false }
        }
    }

    private fun buildPurposeDropdownView(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(R.drawable.bg_ui_card)
        purposeOptions.forEachIndexed { index, purpose ->
            val selected = index == purposeSpinner.selectedItemPosition
            addView(LinearLayout(this@ScannerActivity).apply {
                orientation = LinearLayout.VERTICAL
                minimumHeight = dimenOffset(R.dimen.ui_touch_min)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(
                    dimenOffset(R.dimen.ui_gutter),
                    dimenOffset(R.dimen.space_md),
                    dimenOffset(R.dimen.ui_gutter),
                    dimenOffset(R.dimen.space_md),
                )
                setBackgroundColor(color(if (selected) R.color.ui_primary_wash else R.color.ui_surface))
                setOnClickListener {
                    purposeSpinner.setSelection(index, false)
                    bindSelectedPurposeHeader()
                    renderPurposeDropdown()
                    setPurposeDropdownOpen(false)
                }
                addView(TextView(this@ScannerActivity).apply {
                    text = purpose.displayName()
                    setTextAppearance(R.style.TextAppearance_EventQR_Type_Title)
                    setTextColor(color(R.color.ui_text_primary))
                })
                addView(TextView(this@ScannerActivity).apply {
                    text = purpose.description?.takeIf { it.isNotBlank() } ?: purpose.defaultDescription()
                    setTextAppearance(R.style.TextAppearance_EventQR_Type_BodySmall)
                    setTextColor(color(R.color.ui_text_secondary))
                })
            })
        }
    }

    private fun setPurposeDropdownOpen(open: Boolean) {
        if (open && purposeOptions.isEmpty()) return
        if (open) {
            setEventDropdownOpen(false)
            if (purposePopup == null || contextBar.width > 0 && purposePopup?.width != contextBar.width) renderPurposeDropdown()
            isPurposeDropdownOpen = true
            purposePopup?.showAsDropDown(contextBar, 0, 0)
        } else {
            purposePopup?.dismiss()
            isPurposeDropdownOpen = false
        }
    }

    private fun ScanPurposeResponse.displayName(): String = when (code) {
        ScanPurposeCode.ENTRY -> getString(R.string.scan_purpose_type_event_entry)
        ScanPurposeCode.ATTENDANCE -> getString(R.string.scan_purpose_type_session_attendance)
        ScanPurposeCode.BOOTH_VISIT -> getString(R.string.scan_purpose_type_booth_visit)
        ScanPurposeCode.BENEFIT_CLAIM -> getString(R.string.scan_purpose_type_benefit_claim)
        ScanPurposeCode.REWARD_REDEMPTION, ScanPurposeCode.REWARD_REDEMPTION_SCAN -> getString(R.string.scan_purpose_type_reward_redemption)
        ScanPurposeCode.EXIT -> getString(R.string.scan_purpose_type_event_exit)
        else -> name
    }

    private fun ScanPurposeResponse.defaultDescription(): String = when (code) {
        ScanPurposeCode.ENTRY -> getString(R.string.scanner_purpose_desc_entry)
        ScanPurposeCode.ATTENDANCE -> getString(R.string.scanner_purpose_desc_attendance)
        ScanPurposeCode.BOOTH_VISIT -> getString(R.string.scanner_purpose_desc_booth_visit)
        ScanPurposeCode.BENEFIT_CLAIM -> getString(R.string.scanner_purpose_desc_benefit_claim)
        ScanPurposeCode.REWARD_REDEMPTION, ScanPurposeCode.REWARD_REDEMPTION_SCAN -> getString(R.string.scanner_purpose_desc_reward_redemption)
        ScanPurposeCode.EXIT -> getString(R.string.scanner_purpose_desc_exit)
        else -> getString(R.string.scanner_purpose_desc_default)
    }

    // ── Submission ───────────────────────────────────────────────────────────

    private fun submitCurrentSelection(trigger: String) {
        val selectedEvent = selectedEvent() ?: return showMessage(getString(R.string.scanner_no_assigned_event_selected))
        if (purposeOptions.isEmpty()) return showMessage(getString(R.string.scanner_no_scan_purposes_enabled_for_this_ev))
        val selectedPurpose = purposeOptions.getOrNull(purposeSpinner.selectedItemPosition) ?: return showMessage(getString(R.string.scanner_no_scan_purpose_selected))
        if (!isPurposeAllowed(selectedPurpose)) return showMessage(getString(R.string.staff_print_id_permission_required))
        val qrValue = qrInput.text.toString().trim()
        // Only manual entry can reach here blank (the camera path rejects empty payloads first).
        if (qrValue.isBlank()) return showMessage(getString(R.string.scanner_manual_entry_required))
        val signature = "${selectedEvent.id}|${selectedPurpose.scanPurposeId}|$qrValue"
        val now = SystemClock.elapsedRealtime()
        if (submitInFlight || (signature == lastSubmittedSignature && now - lastSubmittedAtMs < duplicateWindowMs)) {
            return showMessage(getString(R.string.scanner_scan_is_already_being_processed_plea))
        }
        submitInFlight = true
        lastSubmittedSignature = signature
        lastSubmittedAtMs = now
        if (selectedPurpose.code == ScanPurposeCode.REWARD_REDEMPTION_SCAN || selectedPurpose.code == ScanPurposeCode.REWARD_REDEMPTION) {
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
            putExtra(StaffScreenExtras.EXTRA_ELIGIBLE, result.eligible != false)
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
        if (!(trimmed.startsWith("{") && trimmed.endsWith("}"))) return ParsedQrPayload(trimmed, null)
        return runCatching {
            val json = JSONObject(trimmed)
            val qrValue = firstNonBlank(json.optString("qrValue"), json.optString("qr_value"), json.optString("value"))
            val qrCredentialId = firstNonBlank(json.optString("qrCredentialId"), json.optString("qr_credential_id"), json.optString("credentialId"))
            qrValue?.let { ParsedQrPayload(it, qrCredentialId) }
        }.getOrNull()
    }

    private fun firstNonBlank(vararg values: String?): String? = values.firstOrNull { !it.isNullOrBlank() }?.trim()

    private data class ParsedQrPayload(val qrValue: String, val qrCredentialId: String?)

    private companion object {
        const val STATE_CAMERA_PERMISSION_REQUESTED = "scanner_camera_permission_requested"
        const val STATE_CAMERA_PERMISSION_DENIED = "scanner_camera_permission_denied"
        const val BRACKET_FLASH_MS = 600L
        val BRACKET_IDS = intArrayOf(
            R.id.scanBracketTl,
            R.id.scanBracketTr,
            R.id.scanBracketBl,
            R.id.scanBracketBr,
        )
    }
}
