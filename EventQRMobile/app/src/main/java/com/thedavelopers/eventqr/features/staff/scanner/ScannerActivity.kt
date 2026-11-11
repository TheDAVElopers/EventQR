package com.thedavelopers.eventqr.features.staff.scanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.SurfaceTexture
import android.graphics.drawable.ColorDrawable
import android.hardware.Camera
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.TextureView
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.lifecycle.lifecycleScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.api.dto.RegistrationStatus
import com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode
import com.thedavelopers.eventqr.core.api.dto.TransactionResult
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.core.util.UiStrings
import com.thedavelopers.eventqr.features.idprinting.AndroidIdPrinter
import com.thedavelopers.eventqr.features.idprinting.PrintPermission
import com.thedavelopers.eventqr.features.rewards.AppRewardExtras
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionScanResponse
import com.thedavelopers.eventqr.features.scanpurposes.model.dto.ScanPurposeResponse
import com.thedavelopers.eventqr.features.staff.EventSpinnerOption
import com.thedavelopers.eventqr.features.staff.StaffBadges
import com.thedavelopers.eventqr.features.staff.StaffBottomNavItem
import com.thedavelopers.eventqr.features.staff.StaffRepository
import com.thedavelopers.eventqr.features.staff.StaffScreenExtras
import com.thedavelopers.eventqr.features.staff.configureStaffBottomNav
import com.thedavelopers.eventqr.features.staff.model.dto.ScanVerificationResponse
import com.thedavelopers.eventqr.features.staff.result.ScanResultState
import com.thedavelopers.eventqr.features.staff.result.TransactionResultCopy
import com.thedavelopers.eventqr.features.staff.reward.RewardRedemptionScanResultActivity
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse
import com.thedavelopers.eventqr.ui.components.EventQrEmptyState
import com.thedavelopers.eventqr.ui.components.PassHeadBinder
import com.thedavelopers.eventqr.ui.icons.AppIcons
import com.thedavelopers.eventqr.ui.theme.applyEventQrSystemBarAppearance
import com.thedavelopers.eventqr.ui.theme.applyEventQrTopInsetPadding
import com.thedavelopers.eventqr.ui.theme.color
import com.thedavelopers.eventqr.ui.theme.dimenOffset
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Staff scanner (§5.13, §6.3). The camera is the only large dark surface in the app and it is flat.
 *
 * One screen per scan (Slice 1): a decode goes straight to the record endpoint for the selected
 * purpose, and the result is the inline group below the (now hidden) viewport on this same
 * activity. The result is driven by the server row's [TransactionResult] - the row lock is the only
 * decision that counts - never by re-deriving approval on the client. There is no Undo: the backend
 * has no void endpoint, so a ghost Undo would lie.
 *
 * One decoded pass = one [UUID] idempotency key, held across rotation and reused by "Try again".
 * That is what lets a retry after a timeout return the original row instead of logging the attendee
 * twice.
 *
 * Still owned here: the permission flow, camera lifecycle, ZXing decode, the one context sheet,
 * duplicate suppression, the inline error strip, and the ID_PRINT verify + print path.
 */
@Suppress("DEPRECATION")
open class ScannerActivity : AppCompatActivity(), ScannerContract.View, TextureView.SurfaceTextureListener, Camera.PreviewCallback {
    private lateinit var presenter: ScannerPresenter
    private lateinit var repository: StaffRepository
    private lateinit var qrInput: EditText
    private lateinit var resultStrip: TextView
    private lateinit var scanErrorGroup: View
    private lateinit var scanErrorRetry: View
    private lateinit var scrollScanner: ScrollView
    private lateinit var scanViewport: View
    private lateinit var manualEntryGroup: View
    private lateinit var contextBar: View
    private lateinit var contextEvent: TextView
    private lateinit var contextPurpose: TextView
    private lateinit var contextCaret: ImageView
    private lateinit var purposeNote: TextView
    private lateinit var inlineCameraPreview: TextureView
    private lateinit var cameraStatus: TextView
    private lateinit var scannerIcon: ImageView
    private lateinit var cameraContainer: FrameLayout
    private lateinit var scanPanel: View
    private lateinit var guidanceRow: View
    private lateinit var torchButton: ImageView
    private lateinit var resultGroup: View
    private lateinit var resultBlock: View
    private lateinit var resultScope: View
    private lateinit var resultScopeEventRow: View
    private lateinit var resultScopePurposeRow: View
    private lateinit var resultScopeEvent: TextView
    private lateinit var resultScopePurpose: TextView
    private lateinit var resultIcon: ImageView
    private lateinit var resultState: TextView
    private lateinit var resultHint: TextView
    private lateinit var resultAttendee: TextView
    private lateinit var resultRegistrationRow: View
    private lateinit var resultRegistrationBadge: TextView
    private lateinit var resultPointsRow: View
    private lateinit var resultPoints: TextView
    private lateinit var resultDetail: TextView
    private lateinit var resultPrimary: TextView
    private lateinit var resultActionRule: View
    private lateinit var resultActionBar: View
    private lateinit var scanEmptyState: EventQrEmptyState
    private var staffUserId: String? = null

    // False until the role guard passed and every view above is inflated. A non-staff session
    // finishes in onCreate without inflating anything, so onResume / onDestroy must not touch them.
    private var uiReady = false

    private val eventOptions = mutableListOf<EventSpinnerOption>()
    private val purposeOptions = mutableListOf<ScanPurposeResponse>()
    // The selection the deleted off-screen Spinners used to hold. The sheet and the header read
    // these, so the control never carried state of its own.
    private var selectedEventIndex = 0
    private var selectedPurposeIndex = 0
    private var preselectedEventId: String? = null
    // The event whose purposes are already requested, so an adapter change plus an explicit load
    // does not fetch the same list twice.
    private var loadedPurposeEventId: String? = null
    private var eventsLoaded = false
    // The assigned-event load failed: one in-flow error state with a single retry (§5.10), never a
    // toast over a blank screen.
    private var eventsLoadFailed = false
    private var lastSubmittedSignature: String? = null
    private var lastSubmittedAtMs: Long = 0L
    // A pass that was answered without a submission (no purpose yet, duplicate window, bad payload)
    // stays in frame: remembering it stops the same visible code from re-toasting every frame.
    private var lastRejectedPayload: String? = null
    // Written on the UI thread, read on the camera callback thread, which must not touch View state.
    @Volatile private var submitInFlight: Boolean = false
    // The decode path is closed until the selected event's purposes are bound: a frame decoded
    // between an event switch and the new list would post the new event with the old scanPurposeId.
    @Volatile private var scanPurposesReady: Boolean = false
    private var savingPrint: Boolean = false
    private var resultPrimaryAction: (() -> Unit)? = null
    // Mirrors the result group's visibility for the camera callback thread, which must not read
    // View state and must not race the UI thread's writes to submitInFlight.
    @Volatile private var resultVisible = false
    // The rendered result as it stands, so a rotation re-renders the outcome instead of dropping
    // back to the live camera - which would invite a second scan of the same pass.
    private var renderedResult: RenderedResult? = null
    private var isContextSheetOpen = false
    private var contextPopup: PopupWindow? = null
    private var camera: Camera? = null
    private var torchOn = false

    // One decoded pass = one idempotency key. Held across rotation so "Try again" can never log
    // the same pass twice when the first request landed but its response did not come back.
    private var pendingRequestId: UUID? = null
    private var pendingQrValue: String? = null
    private var pendingEventId: String? = null
    private var pendingPurpose: ScanPurposeResponse? = null

    // The event and purpose labels captured when the scan was submitted. The result block restates
    // them from here, never from the live selection, so a later context change or a rotation cannot
    // relabel a finished scan; the ID print card uses the same captured event name.
    private var scanScope: ScanScope? = null

    // ID printing is not a transaction: the system print dialog needs the attendee and the pass
    // value captured at verification time.
    private var printEventId: String? = null
    private var printAttendeeId: String? = null
    private var printAttendeeName: String? = null
    private var printQrValue: String? = null
    // The event label captured when the pass was verified. A replay can complete before loadEvents
    // repopulates the spinner, so the print card never reads an empty name from the live options.
    private var printEventName: String? = null

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
            clearCameraPermissionAction()
            startInlineCameraIfReady()
        } else {
            cameraPermissionDenied = true
            bindCameraPermissionAction()
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

        // The event the user switched to survives rotation; without it showEvents re-selects the
        // intent's event and a later scan could submit under the event the user left.
        preselectedEventId = savedInstanceState?.getString(STATE_SELECTED_EVENT_ID)
            ?: intent.getStringExtra(StaffScreenExtras.EXTRA_EVENT_ID)
        cameraPermissionAutoRequested = savedInstanceState?.getBoolean(STATE_CAMERA_PERMISSION_REQUESTED) ?: false
        cameraPermissionDenied = savedInstanceState?.getBoolean(STATE_CAMERA_PERMISSION_DENIED) ?: false
        restorePendingScan(savedInstanceState)

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

        repository = newRepository()
        presenter = ScannerPresenter(this, repository, UiStrings(this))
        qrInput = findViewById(R.id.edtScannerQr)
        resultStrip = findViewById(R.id.txtScannerResult)
        scanErrorGroup = findViewById(R.id.scanErrorGroup)
        scanErrorRetry = findViewById(R.id.btnScanRetry)
        scrollScanner = findViewById(R.id.scrollScanner)
        scanViewport = findViewById(R.id.scanViewport)
        manualEntryGroup = findViewById(R.id.groupManualEntry)
        contextBar = findViewById(R.id.scanContext)
        contextEvent = findViewById(R.id.scanContextEvent)
        contextPurpose = findViewById(R.id.scanContextPurpose)
        contextCaret = findViewById(R.id.scanContextCaret)
        purposeNote = findViewById(R.id.txtSelectedPurposePoints)
        cameraStatus = findViewById(R.id.scanGuidance)
        scanPanel = findViewById(R.id.scanPanel)
        guidanceRow = findViewById(R.id.scanGuidanceRow)
        torchButton = findViewById(R.id.scanTorch)
        resultGroup = findViewById(R.id.groupScanResult)
        resultBlock = findViewById(R.id.scanResultBlock)
        resultScope = findViewById(R.id.scanResultScope)
        resultScopeEventRow = findViewById(R.id.scanResultScopeEventRow)
        resultScopePurposeRow = findViewById(R.id.scanResultScopePurposeRow)
        resultScopeEvent = findViewById(R.id.scanResultScopeEvent)
        resultScopePurpose = findViewById(R.id.scanResultScopePurpose)
        resultIcon = findViewById(R.id.scanResultIcon)
        resultState = findViewById(R.id.scanResultState)
        resultHint = findViewById(R.id.scanResultHint)
        resultAttendee = findViewById(R.id.scanResultAttendee)
        resultRegistrationRow = findViewById(R.id.scanResultRegistrationRow)
        resultRegistrationBadge = findViewById(R.id.scanResultRegistrationBadge)
        resultPointsRow = findViewById(R.id.scanResultPointsRow)
        resultPoints = findViewById(R.id.scanResultPoints)
        resultDetail = findViewById(R.id.scanResultDetail)
        resultPrimary = findViewById(R.id.btnScanResultPrimary)
        resultActionRule = findViewById(R.id.resultActionRule)
        resultActionBar = findViewById(R.id.resultActionBar)
        scanEmptyState = findViewById<EventQrEmptyState>(R.id.txtScannerEmptyState)
        staffUserId = sessionManager.getUserId()

        // The shared viewport's preview slot gets the camera surface, behind the frame + brackets.
        val previewSlot = findViewById<FrameLayout>(R.id.scanPreview)
        val preview = layoutInflater.inflate(R.layout.ui_staff_scan_preview, previewSlot, false)
        previewSlot.addView(preview, 0)
        inlineCameraPreview = preview.findViewById(R.id.textureInlineCameraPreview)
        scannerIcon = preview.findViewById(R.id.imgScannerIcon)
        cameraContainer = preview.findViewById(R.id.frameCameraContainer)

        // The 240dp preview square is a rounded frame (bg_ui_scan_frame, ui_radius_card §5.13), so
        // the camera surface must not paint into the corner pockets outside that 10dp arc. Clip only
        // the camera well: the hairline and the corner brackets stay above it and stay unclipped.
        val previewRadius = resources.getDimension(R.dimen.ui_radius_card)
        cameraContainer.clipToOutline = true
        cameraContainer.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, previewRadius)
            }
        }

        resultStrip.visibility = View.GONE
        scanErrorGroup.visibility = View.GONE
        scanErrorRetry.setOnClickListener { retryScanError() }
        resultGroup.visibility = View.GONE
        resultPrimary.setOnClickListener { resultPrimaryAction?.invoke() }
        inlineCameraPreview.surfaceTextureListener = this

        contextBar.setOnClickListener { setContextSheetOpen(!isContextSheetOpen) }
        torchButton.setOnClickListener { toggleTorch() }

        configureStaffBottomNav(StaffBottomNavItem.SCANNER, selectedEvent()?.id)

        scanPanel.setOnClickListener { requestInlineCameraStart(userInitiated = true) }
        findViewById<View>(R.id.btnSubmitScan).setOnClickListener { submitCurrentSelection() }
        bindScanChrome()
        // A rotation while a request was in flight replays the same request id, so the server
        // returns its stored row instead of the camera logging the pass a second time. Otherwise a
        // result that was already rendered is put back on screen.
        if (pendingRequestId != null) replayPendingScan() else restoreRenderedResult(savedInstanceState)
        uiReady = true
        presenter.loadEvents()
    }

    @VisibleForTesting
    internal open fun newRepository(): StaffRepository = StaffRepository(this)

    override fun onResume() {
        super.onResume()
        if (!uiReady) return
        // The camera prompt follows the screen taking focus, never the async event load: gating it
        // on a loaded event list left a cold open with a black panel, a "point the camera" line and
        // nothing to tap. The auto-request guard keeps this to one system prompt per screen open.
        if (!resultShown() && !eventsLoadFailed && (!eventsLoaded || hasSelectableEvent())) {
            requestInlineCameraStart(userInitiated = false)
        }
    }

    override fun onPause() { releaseInlineCamera(); contextPopup?.dismiss(); super.onPause() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_CAMERA_PERMISSION_REQUESTED, cameraPermissionAutoRequested)
        outState.putBoolean(STATE_CAMERA_PERMISSION_DENIED, cameraPermissionDenied)
        outState.putString(STATE_SELECTED_EVENT_ID, selectedEvent()?.id ?: preselectedEventId)
        outState.putString(STATE_PENDING_REQUEST_ID, pendingRequestId?.toString())
        outState.putString(STATE_PENDING_QR, pendingQrValue)
        outState.putString(STATE_PENDING_EVENT_ID, pendingEventId)
        outState.putString(STATE_PENDING_PURPOSE_ID, pendingPurpose?.scanPurposeId?.toString())
        outState.putString(STATE_PENDING_PURPOSE_CODE, pendingPurpose?.code?.name)
        outState.putString(STATE_PENDING_PURPOSE_NAME, pendingPurpose?.name)
        outState.putString(STATE_SCOPE_EVENT, scanScope?.eventLabel)
        outState.putString(STATE_SCOPE_PURPOSE, scanScope?.purposeLabel)
        outState.putString(STATE_LAST_SIGNATURE, lastSubmittedSignature)
        // elapsedRealtime survives the activity, so the 2s duplicate window keeps working across
        // a rotation instead of letting the same pass through twice.
        outState.putLong(STATE_LAST_SUBMITTED_AT, lastSubmittedAtMs)
        renderedResult?.takeIf { it.resumable }?.let { rendered ->
            outState.putString(STATE_RESULT_TONE, rendered.tone.name)
            outState.putString(STATE_RESULT_STATE, rendered.state)
            outState.putString(STATE_RESULT_HINT, rendered.hint)
            outState.putString(STATE_RESULT_ATTENDEE, rendered.attendee)
            outState.putString(STATE_RESULT_REGISTRATION, rendered.registrationStatus?.name)
            outState.putInt(STATE_RESULT_POINTS, rendered.points ?: NO_POINTS)
            outState.putString(STATE_RESULT_DETAIL, rendered.detail)
            // The print panel's action is only meaningful with the pass it was verified against.
            printContext()?.let { context ->
                outState.putString(STATE_PRINT_EVENT_ID, context.eventId)
                outState.putString(STATE_PRINT_ATTENDEE_ID, context.attendeeId)
                outState.putString(STATE_PRINT_ATTENDEE_NAME, context.attendeeName)
                outState.putString(STATE_PRINT_QR, context.qrValue)
                outState.putString(STATE_PRINT_EVENT_NAME, printEventName)
            }
            outState.putBoolean(STATE_PRINTING, savingPrint)
        }
    }
    override fun onDestroy() {
        cameraPermissionDialog?.dismiss()
        if (uiReady) presenter.detach()
        contextPopup?.dismiss()
        releaseInlineCamera()
        decoderExecutor.shutdownNow()
        super.onDestroy()
    }

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
        if (data == null || camera == null || submitInFlight || resultVisible || !scanPurposesReady || !decoding.compareAndSet(false, true)) return
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
        // A fresh list re-derives the purposes, so the de-dupe marker starts empty; the selection
        // bind and the explicit load below then fetch the list exactly once.
        eventsLoaded = true
        eventsLoadFailed = false
        bindScanEmptyState()
        loadedPurposeEventId = null
        // Same preselection rule the Spinner carried: the requested event when it is assigned,
        // otherwise the first one.
        selectedEventIndex = items.indexOfFirst { it.id == preselectedEventId }.coerceAtLeast(0)
        bindSelectedEventHeader()
        loadSelectedPurposes()
        bindScanChrome()
        // The camera may have been asked for before the list arrived (cold open); with an event to
        // scan for, start it now instead of waiting for the next resume.
        if (!resultShown() && hasSelectableEvent()) requestInlineCameraStart(userInitiated = false)
    }

    /**
     * A failed assigned-event load is an in-flow state, not a toast over a blank screen (§5.10 error
     * variant): the shared empty state takes the content slot, speaks the server reason verbatim and
     * carries the one action - a retry of the same load.
     */
    override fun showEventsLoadFailed(message: String) {
        eventsLoadFailed = true
        bindScanEmptyState()
        scanEmptyState.show(getString(R.string.ui_staff_events_error_title), message)
        scanEmptyState.setAction(getString(R.string.common_retry), primary = false) { presenter.loadEvents() }
        bindScanChrome()
    }

    /** The no-event and load-failed states share one slot, so only one copy is ever bound to it. */
    private fun bindScanEmptyState() {
        if (eventsLoadFailed) return
        scanEmptyState.setIcon(AppIcons.Event)
        scanEmptyState.setContent(getString(R.string.ui_staff_no_events), getString(R.string.ui_staff_no_events_body))
        scanEmptyState.setAction(null, null)
    }

    override fun showPurposes(items: List<ScanPurposeResponse>) {
        // ID printing is only offered while the caller holds the Print IDs permission for the event.
        val activePurposes = items.filter { it.active && isPurposeAllowed(it) }
        purposeOptions.clear()
        purposeOptions.addAll(activePurposes)
        // One choke point for the decode gate: only a bound, non-empty list can start a scan.
        scanPurposesReady = activePurposes.isNotEmpty()
        selectedPurposeIndex = 0
        if (activePurposes.isEmpty()) {
            contextPurpose.text = getString(R.string.scanner_no_scan_purposes_enabled)
            contextPurpose.visibility = View.VISIBLE
            purposeNote.text = getString(
                if (items.any { it.active && it.code == ScanPurposeCode.ID_PRINT }) {
                    R.string.staff_print_id_permission_required
                } else {
                    R.string.scanner_configure_scan_purposes_first
                },
            )
            bindPurposeNote()
            bindContextAffordance()
        } else {
            bindSelectedPurposeHeader()
        }
    }

    /**
     * A failed purpose load drops the list the previous event left behind: keeping it would let the
     * next scan post the newly selected event with the old event's `scanPurposeId`. The de-dupe
     * marker is cleared too, so re-selecting the same event retries the load instead of trusting a
     * list that never arrived.
     */
    override fun showPurposesUnavailable(message: String) {
        loadedPurposeEventId = null
        showPurposes(emptyList())
        showMessage(message)
    }

    override fun showRecordedResult(result: TransactionResponse) {
        submitInFlight = false
        clearPendingScan()
        bindRecordedResult(result)
    }

    override fun showVerifiedResult(result: ScanVerificationResponse) {
        submitInFlight = false
        bindVerifiedResult(result)
    }

    override fun showRecordFailed(message: String) {
        submitInFlight = false
        bindSaveFailed(message)
    }

    override fun showRewardRedemptionScanResult(result: RewardRedemptionScanResponse) {
        submitInFlight = false
        // Redemption has its own endpoint and no idempotency key, so its pass must never be left
        // behind for the replay path to re-submit through the transaction endpoint.
        clearPendingScan()
        hideScanStrip()
        // The 2s duplicate window is the only guard left once this screen is covered: stop the
        // camera before the result opens so the pass cannot be decoded again behind it.
        releaseInlineCamera()
        openRewardRedemptionScanResult(result)
    }

    override fun showScanError(message: String) {
        submitInFlight = false
        resetBracketTint()
        // An ID_PRINT failure is a verification (lookup) failure, not a save: no transaction row
        // exists to retry behind an idempotency key, so "Scan not saved" would be a lie. The server
        // reason goes in the inline strip and the screen returns to the live camera.
        if (pendingPurpose?.code == ScanPurposeCode.ID_PRINT) {
            if (resultShown()) scanNext()
            showScanStrip(message)
            return
        }
        if (resultShown()) {
            if (hasPendingScan()) {
                // A failed replay keeps its pass and its idempotency key: the save-failed panel's
                // "Try again" is the way out, not a neutral "Saving scan…" with a dead primary.
                bindSaveFailed(message)
                hideScanStrip()
                return
            }
            // Nothing to retry: hand the screen back to the live camera with the strip, so the
            // next pass is scannable immediately.
            scanNext()
        }
        showScanStrip(message)
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showLoading(isLoading: Boolean) {
        findViewById<View>(R.id.btnSubmitScan).isEnabled = !isLoading
        // A replay is still in flight while the events/purposes load settles, so a request-bound
        // primary ("Saving scan…", no action) must stay disabled, not become a tappable no-op. An
        // ID print in flight owns the same primary, so a load settling mid-print leaves it disabled.
        if (resultShown()) {
            resultPrimary.isEnabled = !isLoading && !submitInFlight && !savingPrint && resultPrimaryAction != null
        }
    }

    /** The inline failure strip always carries its own Retry (§9.6:18): a decode failure is not a dead end. */
    private fun showScanStrip(message: String) {
        resultStrip.text = message
        resultStrip.visibility = View.VISIBLE
        scanErrorGroup.visibility = View.VISIBLE
    }

    private fun hideScanStrip() {
        resultStrip.text = ""
        // The strip and its Retry are one state: leaving either VISIBLE inside a hidden group is
        // what let a stale banner sit over a later result.
        resultStrip.visibility = View.GONE
        scanErrorGroup.visibility = View.GONE
    }

    /**
     * The strip's one action. A scan still held for a retry re-sends with its original idempotency
     * key; a failure with nothing held (bad payload, reward scan) hands the screen back to the live
     * camera instead.
     */
    private fun retryScanError() {
        hideScanStrip()
        if (hasPendingScan()) retryPendingScan() else scanNext()
    }

    // ── Inline result (§6.3, one screen) ─────────────────────────────────────

    private enum class ResultTone { SUCCESS, ERROR, NEUTRAL }

    /** The block's own values, kept for [onSaveInstanceState] so a rotation re-renders them. */
    private data class RenderedResult(
        val tone: ResultTone,
        val state: String,
        val hint: String,
        val attendee: String?,
        val registrationStatus: RegistrationStatus?,
        val points: Int?,
        val detail: String?,
        val resumable: Boolean,
    )

    private fun resultShown(): Boolean = ::resultGroup.isInitialized && resultGroup.visibility == View.VISIBLE

    private fun hasSelectableEvent(): Boolean = selectedEvent() != null

    /** A live scan surface: an assigned event exists and no result owns the screen. */
    private fun scannerChromeVisible(): Boolean = hasSelectableEvent() && !resultShown()

    /**
     * The one rule for the purpose micro line. It needs the guidance row above it, so it is GONE
     * with the chrome; a tracking-only purpose has no description to add, so it is GONE then too.
     * Otherwise it shows whatever text the header binders put on it.
     */
    private fun bindPurposeNote() {
        val trackingOnly = purposeOptions.getOrNull(selectedPurposeIndex)?.trackingOnly == true
        purposeNote.visibility =
            if (scannerChromeVisible() && !trackingOnly && purposeNote.text.isNotBlank()) View.VISIBLE else View.GONE
    }

    /**
     * The camera, its guidance row and manual entry exist only while there is something to scan
     * against and no result is on screen. With no assigned event the context field is the single
     * directed state - no dead camera panel, no dead entry field (§5.13) - and the shared empty
     * state below it says what to do next.
     */
    private fun bindScanChrome() {
        val visibility = if (scannerChromeVisible()) View.VISIBLE else View.GONE
        // The result owns the screen (§5.13 v1.9): the whole viewport include goes with the result,
        // context field included. The captured scope is restated in the result group, so the bar
        // never competes with a finished scan for the same setting.
        scanViewport.visibility = if (resultShown()) View.GONE else View.VISIBLE
        scanPanel.visibility = visibility
        guidanceRow.visibility = visibility
        manualEntryGroup.visibility = visibility
        // The purpose micro line is part of the guidance row (§5.13), so it follows the chrome:
        // with the panel gone it must not float alone under the context bar.
        bindPurposeNote()
        // Only after the load settles: an empty state shown while events are still loading would
        // claim there are none.
        scanEmptyState.visibility =
            if ((eventsLoaded || eventsLoadFailed) && !hasSelectableEvent() && !resultShown()) View.VISIBLE else View.GONE
        bindContextAffordance()
    }

    private fun bindResultBlock(
        tone: ResultTone,
        state: String,
        hint: String,
        attendee: String?,
        registrationStatus: RegistrationStatus?,
        points: Int?,
        detail: String?,
    ) {
        val neutral = tone == ResultTone.NEUTRAL
        // The captured scope, always from the submitted values - never the live selection (§6.3).
        // Two metric rows on ui_background under the block (v1.11), so one colour pair fits every
        // tone. A row appears only when its captured value is non-blank; the container only when
        // at least one is. Each row is one TalkBack node: "Event name: {value}".
        val scope = scanScope
        val scopeEvent = scope?.eventLabel.orEmpty()
        val scopePurpose = scope?.purposeLabel.orEmpty()
        val hasScopeEvent = scopeEvent.isNotBlank()
        val hasScopePurpose = scopePurpose.isNotBlank()
        resultScope.visibility = if (hasScopeEvent || hasScopePurpose) View.VISIBLE else View.GONE
        resultScopeEventRow.visibility = if (hasScopeEvent) View.VISIBLE else View.GONE
        resultScopePurposeRow.visibility = if (hasScopePurpose) View.VISIBLE else View.GONE
        resultScopeEvent.text = scopeEvent
        resultScopePurpose.text = scopePurpose
        resultScopeEventRow.contentDescription =
            if (hasScopeEvent) getString(R.string.ui_staff_scope_event_a11y, scopeEvent) else null
        resultScopePurposeRow.contentDescription =
            if (hasScopePurpose) getString(R.string.ui_staff_scope_purpose_a11y, scopePurpose) else null
        resultBlock.setBackgroundColor(
            color(
                when (tone) {
                    ResultTone.SUCCESS -> R.color.ui_success
                    ResultTone.ERROR -> R.color.ui_error
                    ResultTone.NEUTRAL -> R.color.ui_surface_sunken
                },
            ),
        )
        resultIcon.setImageResource(
            when (tone) {
                ResultTone.SUCCESS -> AppIcons.Success
                ResultTone.ERROR -> AppIcons.Error
                ResultTone.NEUTRAL -> AppIcons.Scan
            },
        )
        ImageViewCompat.setImageTintList(
            resultIcon,
            ColorStateList.valueOf(color(if (neutral) R.color.ui_text_secondary else R.color.ui_on_primary)),
        )
        resultState.setTextColor(color(if (neutral) R.color.ui_text_primary else R.color.ui_on_primary))
        resultState.text = state
        resultHint.setTextColor(color(if (neutral) R.color.ui_text_secondary else R.color.ui_on_primary))
        resultHint.text = hint
        resultHint.visibility = if (hint.isBlank()) View.GONE else View.VISIBLE

        resultAttendee.text = attendee ?: getString(R.string.ui_staff_result_attendee)
        resultAttendee.visibility = if (attendee.isNullOrBlank()) View.GONE else View.VISIBLE

        // Registration STATE, not this scan's verdict: the server sends the registration's current
        // status on every recorded row, including a rejection that changed nothing, so on a failed
        // scan that status belongs to an earlier scan. The row is therefore shown only on a
        // confirmation, where the state is the fact the accepted scan left behind; a lime badge
        // under a rejection would confirm the opposite of the block (lime is confirmation-only).
        val registrationFact = registrationStatus.takeIf { tone == ResultTone.SUCCESS }
        resultRegistrationRow.visibility = if (registrationFact == null) View.GONE else View.VISIBLE
        if (registrationFact != null) StaffBadges.bind(resultRegistrationBadge, registrationFact)

        resultPointsRow.visibility = if (points == null) View.GONE else View.VISIBLE
        if (points != null) resultPoints.text = getString(R.string.ui_staff_result_points_value, points)

        resultDetail.text = detail.orEmpty()
        resultDetail.visibility = if (detail.isNullOrBlank()) View.GONE else View.VISIBLE
        // Not resumable until a persistent action ("Scan next") is bound: a print or retry panel
        // must not come back without the context that makes its action work.
        renderedResult = RenderedResult(tone, state, hint, attendee, registrationStatus, points, detail, resumable = false)
    }

    /**
     * The pinned action (§6.3 v1.9): the bar is visible only while the panel owns an action. The
     * neutral replay panel and the live camera have nothing to leave, so the bar is GONE there.
     */
    private fun bindResultActionBar() {
        val visible = resultShown() && resultPrimaryAction != null
        resultActionRule.visibility = if (visible) View.VISIBLE else View.GONE
        resultActionBar.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun showResultGroup(show: Boolean) {
        resultGroup.visibility = if (show) View.VISIBLE else View.GONE
        resultVisible = show
        bindScanChrome()
        bindResultActionBar()
        if (!show) return
        // The panel is gone with the result, so the preview (and its torch) must be released:
        // a live camera behind an invisible panel only burns battery and keeps the indicator on.
        // "Scan next" restarts it.
        releaseInlineCamera()
        scrollScanner.post { scrollScanner.smoothScrollTo(0, 0) }
        val announcement = listOf(resultState.text, resultHint.text)
            .filter { it.isNotBlank() }
            .joinToString(". ")
        resultGroup.post { resultGroup.announceForAccessibility(announcement) }
    }

    private fun bindRecordedResult(result: TransactionResponse) {
        val approved = result.transactionResult == TransactionResult.APPROVED
        val pointsDelta = result.pointsDelta
        // Approved copy follows the purpose the staff actually used: an EXIT, booth visit, claim or
        // attendance never renders as "Checked in" (§6.3).
        bindResultBlock(
            tone = if (approved) ResultTone.SUCCESS else ResultTone.ERROR,
            state = getString(
                if (approved) {
                    TransactionResultCopy.approvedStateRes(result.transactionType)
                } else {
                    R.string.ui_staff_result_rejected
                },
            ),
            hint = getString(
                if (approved) {
                    TransactionResultCopy.approvedHintRes(result.transactionType, pointsDelta)
                } else {
                    TransactionResultCopy.hintRes(approved = false, pointsDelta)
                },
            ),
            attendee = result.attendeeName?.takeIf { it.isNotBlank() },
            registrationStatus = parseRegistrationStatus(result.registrationStatus),
            points = if (TransactionResultCopy.showsPoints(approved, pointsDelta)) pointsDelta else null,
            detail = result.reason?.takeIf { it.isNotBlank() },
        )
        setScanNextPrimary()
        showResultGroup(true)
    }

    /**
     * ID printing has no transaction row to read a decision from, so the verified pass is rendered
     * as a confirmation and the print endpoint stays the authority on whether it may print.
     */
    private fun bindVerifiedResult(result: ScanVerificationResponse) {
        val state = ScanResultState.from(
            qrActive = result.qrActive,
            registrationStatus = result.registrationStatus.name,
            eligible = result.eligible != false,
        )
        val detail = result.message?.takeIf { it.isNotBlank() }
        // The print context belongs to one verified pass; a different pass must never inherit it.
        clearPrintContext()
        when (state) {
            ScanResultState.ACTIVE -> {
                capturePrintContext(result)
                bindResultBlock(
                    tone = ResultTone.SUCCESS,
                    state = getString(R.string.ui_staff_result_verified),
                    hint = getString(R.string.ui_staff_result_verified_hint),
                    attendee = result.attendeeName?.takeIf { it.isNotBlank() },
                    registrationStatus = result.registrationStatus,
                    points = null,
                    detail = detail,
                )
                bindPrintPrimary()
            }
            ScanResultState.INACTIVE -> bindBlockedVerifiedResult(
                state = getString(R.string.ui_staff_result_inactive),
                hint = getString(R.string.ui_staff_result_inactive_hint),
                result = result,
                detail = detail,
            )
            ScanResultState.NOT_ELIGIBLE -> bindBlockedVerifiedResult(
                state = getString(R.string.ui_staff_result_not_allowed),
                hint = getString(R.string.ui_staff_result_not_allowed_hint),
                result = result,
                detail = detail,
            )
        }
        clearPendingScan()
        showResultGroup(true)
    }

    private fun bindBlockedVerifiedResult(
        state: String,
        hint: String,
        result: ScanVerificationResponse,
        detail: String?,
    ) {
        bindResultBlock(
            tone = ResultTone.ERROR,
            state = state,
            hint = hint,
            attendee = result.attendeeName?.takeIf { it.isNotBlank() },
            registrationStatus = result.registrationStatus,
            points = null,
            detail = detail,
        )
        setScanNextPrimary()
    }

    private fun bindSaveFailed(message: String) {
        bindResultBlock(
            tone = ResultTone.ERROR,
            state = getString(R.string.ui_staff_result_not_saved),
            hint = getString(R.string.ui_staff_result_not_saved_hint),
            attendee = null,
            registrationStatus = null,
            points = null,
            detail = message.takeIf { it.isNotBlank() },
        )
        resultPrimary.text = getString(R.string.ui_staff_result_try_again)
        resultPrimary.isEnabled = true
        resultPrimaryAction = { retryPendingScan() }
        showResultGroup(true)
    }

    private fun setScanNextPrimary() {
        // The captured pass belongs to the print panel only. Leaving it behind would let a later
        // restore (or a "Print ID" re-render) come back for a pass this panel no longer shows.
        clearPrintContext()
        resultPrimary.text = getString(R.string.ui_staff_result_scan_next)
        resultPrimary.isEnabled = true
        resultPrimaryAction = { scanNext() }
        // "Scan next" needs no captured context, so the panel survives a rotation as rendered.
        renderedResult = renderedResult?.copy(resumable = true)
    }

    /**
     * The verified ID_PRINT panel's one action. It is resumable only while the pass it was verified
     * against is still held ([printContext]), because the print call needs that context.
     */
    private fun bindPrintPrimary() {
        resultPrimary.text = getString(R.string.ui_staff_result_print_id)
        resultPrimary.isEnabled = true
        resultPrimaryAction = { printPendingId() }
        renderedResult = renderedResult?.copy(resumable = printContext() != null)
    }

    /** Clears the result and hands the screen back to the live camera, same event and purpose. */
    private fun scanNext() {
        clearPendingScan()
        clearPrintContext()
        scanScope = null
        submitInFlight = false
        lastRejectedPayload = null
        qrInput.setText("")
        hideScanStrip()
        resetBracketTint()
        renderedResult = null
        resultPrimaryAction = null
        resultPrimary.isEnabled = true
        resultPrimary.text = getString(R.string.ui_staff_result_scan_next)
        showResultGroup(false)
        scanPanel.post { requestInlineCameraStart(userInitiated = false) }
    }

    /** Puts back the result that was on screen when the activity was saved. */
    private fun restoreRenderedResult(state: Bundle?) {
        if (state == null) return
        val tone = state.getString(STATE_RESULT_TONE)?.let { runCatching { ResultTone.valueOf(it) }.getOrNull() } ?: return
        bindResultBlock(
            tone = tone,
            state = state.getString(STATE_RESULT_STATE).orEmpty(),
            hint = state.getString(STATE_RESULT_HINT).orEmpty(),
            attendee = state.getString(STATE_RESULT_ATTENDEE),
            registrationStatus = state.getString(STATE_RESULT_REGISTRATION)
                ?.let { runCatching { RegistrationStatus.valueOf(it) }.getOrNull() },
            points = state.getInt(STATE_RESULT_POINTS, NO_POINTS).takeIf { it != NO_POINTS },
            detail = state.getString(STATE_RESULT_DETAIL),
        )
        // The captured pass decides the action: a verified ID_PRINT panel comes back as itself,
        // never as a live camera that would invite a second scan of the same pass. A print that
        // was in flight when the activity went away has an unknown outcome, so its action comes
        // back live instead of a dead "Printing…" primary.
        if (printContext() != null) bindPrintPrimary() else setScanNextPrimary()
        showResultGroup(true)
    }

    private fun replayPendingScan() {
        val requestId = pendingRequestId ?: return
        val eventId = pendingEventId ?: return
        val purpose = pendingPurpose ?: return
        val qrValue = pendingQrValue ?: return
        submitInFlight = true
        bindResultBlock(
            tone = ResultTone.NEUTRAL,
            state = getString(R.string.ui_staff_result_logging),
            hint = "",
            attendee = null,
            registrationStatus = null,
            points = null,
            detail = null,
        )
        resultPrimary.text = getString(R.string.ui_staff_result_logging)
        resultPrimary.isEnabled = false
        resultPrimaryAction = null
        showResultGroup(true)
        if (purpose.code == ScanPurposeCode.ID_PRINT) {
            presenter.verifyScan(eventId, purpose, qrValue, staffUserId)
        } else {
            presenter.recordScan(eventId, purpose, qrValue, staffUserId, requestId)
        }
    }

    private fun retryPendingScan() {
        if (submitInFlight) return
        val eventId = pendingEventId ?: return
        val purpose = pendingPurpose ?: return
        val qrValue = pendingQrValue ?: return
        val requestId = pendingRequestId ?: return
        submitInFlight = true
        resultPrimary.isEnabled = false
        resultPrimary.text = getString(R.string.ui_staff_result_logging)
        if (purpose.code == ScanPurposeCode.ID_PRINT) {
            presenter.verifyScan(eventId, purpose, qrValue, staffUserId)
        } else {
            presenter.recordScan(eventId, purpose, qrValue, staffUserId, requestId)
        }
    }

    private fun parseRegistrationStatus(raw: String?): RegistrationStatus? =
        raw?.trim()?.takeIf { it.isNotBlank() }
            ?.let { runCatching { RegistrationStatus.valueOf(it.uppercase()) }.getOrNull() }

    // ── Camera ───────────────────────────────────────────────────────────────

    /**
     * The camera permission is off: the panel is the one tap target and its guidance line is the
     * permission CTA. The torch needs a camera, so it leaves with it.
     */
    private fun bindCameraPermissionAction() {
        cameraStatus.text = getString(R.string.scanner_camera_permission_tap_to_enable)
        torchButton.visibility = View.GONE
        scannerIcon.visibility = View.VISIBLE
    }

    /** The permission is back: the live-camera chrome returns before the camera starts. */
    private fun clearCameraPermissionAction() {
        torchButton.visibility = View.VISIBLE
    }

    private fun requestInlineCameraStart(userInitiated: Boolean) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraPermissionDenied = false
            // Also covers a grant made on the system settings page, which never reaches the launcher.
            clearCameraPermissionAction()
            startInlineCameraIfReady()
            return
        }
        if (!userInitiated && cameraPermissionAutoRequested) {
            bindCameraPermissionAction()
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
                bindCameraPermissionAction()
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
        // The result owns the screen: its panel is gone, so the camera stays closed until
        // "Scan next" asks for it again.
        if (resultShown()) return
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
            // The same unreadable code held in frame must not re-answer every frame.
            if (rawValue == lastRejectedPayload) return
            lastRejectedPayload = rawValue
            showScanError(getString(R.string.scanner_qr_payload_format_is_invalid))
            return
        }
        // A pass this screen just refused to submit is still in front of the camera on the next
        // frame; answering it again would repeat the toast until the pass left the frame.
        if (parsed.qrValue == lastRejectedPayload) return
        flashBrackets()
        qrInput.setText(parsed.qrValue)
        qrInput.setSelection(parsed.qrValue.length)
        lastRejectedPayload = if (submitCurrentSelection()) null else parsed.qrValue
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
        val event = selectedEvent() ?: return
        // The adapter's selection callback and the explicit call after a load describe the same
        // event: one event change means one request.
        if (event.id == loadedPurposeEventId) return
        loadedPurposeEventId = event.id
        presenter.loadPurposes(event.id)
    }

    @VisibleForTesting
    internal fun selectedEvent(): EventSpinnerOption? = eventOptions.getOrNull(selectedEventIndex)

    /** The purpose the next scan posts, or null while no list is bound. */
    @VisibleForTesting
    internal fun selectedPurpose(): ScanPurposeResponse? = purposeOptions.getOrNull(selectedPurposeIndex)

    /**
     * Every purpose is offered except ID printing, which needs the caller's Print IDs permission for
     * the selected event (an event with no permission flag of its own - the unassigned caller,
     * organizer or ADMIN - keeps it available and the server decides).
     */
    private fun isPurposeAllowed(purpose: ScanPurposeResponse): Boolean =
        purpose.code != ScanPurposeCode.ID_PRINT || PrintPermission.isPrintAllowed(selectedEvent()?.canPrintId)

    private fun bindSelectedEventHeader() {
        val event = selectedEvent()
        contextEvent.text = event?.label ?: getString(R.string.ui_staff_no_events)
        contextPurpose.visibility = if (event == null) View.GONE else View.VISIBLE
        bindContextAffordance()
    }

    private fun bindSelectedPurposeHeader() {
        val purpose = selectedPurpose()
        if (purpose == null) {
            contextPurpose.text = ""
            contextPurpose.visibility = View.GONE
            // No purpose, no description: clearing the text keeps [bindPurposeNote] honest when
            // the previous purpose's line is put back.
            purposeNote.text = ""
            bindPurposeNote()
            bindContextAffordance()
            return
        }
        contextPurpose.text = purpose.displayName()
        contextPurpose.visibility = View.VISIBLE
        purposeNote.text = if (purpose.trackingOnly) "" else purpose.defaultDescription()
        bindPurposeNote()
        bindContextAffordance()
    }

    /** One node for one control: the bar speaks both values, including the ellipsized event name. */
    private fun bindContextAffordance() {
        val eventLabel = selectedEvent()?.label.orEmpty()
        val purpose = contextPurpose.text
        val scanningFor = eventLabel.isNotBlank() && purposeOptions.isNotEmpty() && purpose.isNotBlank()
        val interactive = scanningFor && (eventOptions.size > 1 || purposeOptions.size > 1)
        contextBar.isClickable = interactive
        contextBar.isFocusable = interactive
        contextCaret.visibility = if (interactive) View.VISIBLE else View.GONE
        contextBar.contentDescription = if (scanningFor) getString(
            R.string.scanner_context_a11y,
            eventLabel,
            purpose,
        ) else contextEvent.text
        scanPanel.contentDescription = if (scanningFor) getString(
            R.string.ui_scan_panel_description_context,
            eventLabel,
            purpose,
        ) else getString(R.string.ui_scan_panel_description)
    }

    // ── Context sheet (one control, one sheet - §5.13) ───────────────────────

    private fun setContextSheetOpen(open: Boolean) {
        if (open && eventOptions.isEmpty() && purposeOptions.isEmpty()) return
        if (!open) {
            contextPopup?.dismiss()
            isContextSheetOpen = false
            return
        }
        renderContextSheet()
        isContextSheetOpen = true
        contextPopup?.showAsDropDown(contextBar, 0, dimenOffset(R.dimen.space_4))
    }

    private fun renderContextSheet() {
        contextPopup?.dismiss()
        val width = contextBar.width.takeIf { it > 0 }
            ?: (resources.displayMetrics.widthPixels - 2 * dimenOffset(R.dimen.ui_gutter))
        val content = ScrollView(this).apply {
            isFillViewport = false
            addView(buildContextSheetView())
        }
        content.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val cap = (resources.displayMetrics.heightPixels * CONTEXT_SHEET_MAX_SCREEN_FRACTION).toInt()
        contextPopup = PopupWindow(content, width, minOf(content.measuredHeight, cap), true).apply {
            isOutsideTouchable = true
            elevation = dimenOffset(R.dimen.ui_sheet_elevation).toFloat()
            setBackgroundDrawable(ColorDrawable(color(R.color.ui_surface)))
            setOnDismissListener { isContextSheetOpen = false }
        }
    }

    private fun buildContextSheetView(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(R.drawable.bg_ui_card)
        addView(sectionLabel(R.string.scanner_context_event))
        addView(buildEventDropdownView())
        addView(sectionLabel(R.string.scanner_context_purpose))
        addView(buildPurposeDropdownView())
    }

    private fun sectionLabel(resId: Int): TextView = TextView(this).apply {
        setText(resId)
        setTextAppearance(R.style.TextAppearance_EventQR_Type_Overline)
        setTextColor(color(R.color.ui_text_secondary))
        setPaddingRelative(
            dimenOffset(R.dimen.ui_gutter),
            dimenOffset(R.dimen.space_12),
            dimenOffset(R.dimen.ui_gutter),
            dimenOffset(R.dimen.space_4),
        )
    }

    private fun buildEventDropdownView(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        eventOptions.forEachIndexed { index, event ->
            addView(buildContextRow(
                titleText = event.label,
                subtitleText = event.eventStartAt?.atZone(manilaZone)?.format(dateFormatter).orEmpty()
                    .ifBlank { getString(R.string.scanner_assigned_event_fallback) },
                selected = index == selectedEventIndex,
                onCommit = { commitEventSelection(index) },
            ))
        }
    }

    private fun buildPurposeDropdownView(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        purposeOptions.forEachIndexed { index, purpose ->
            addView(buildContextRow(
                titleText = purpose.displayName(),
                subtitleText = purpose.description?.takeIf { it.isNotBlank() } ?: purpose.defaultDescription(),
                selected = index == selectedPurposeIndex,
                onCommit = {
                    selectedPurposeIndex = index
                    // A new purpose can accept a pass the previous one refused.
                    lastRejectedPayload = null
                    bindSelectedPurposeHeader()
                    setContextSheetOpen(false)
                    announceContext()
                },
            ))
        }
    }

    /**
     * One event commit. The held pass, its idempotency key and the result on screen all belong to
     * the previous event, so they leave together: a stale "Try again" would re-submit the previous
     * event's scan under the event now selected. The submit already in flight belongs to that event
     * too, so it is cancelled before the new context exists.
     */
    @VisibleForTesting
    internal fun commitEventSelection(index: Int) {
        // Tapping the row that is already selected is not a switch: it must not cancel a save in
        // flight, and it must not clear the purposes, because no reload would put them back. It
        // re-asks for the list only when the last load left none.
        if (index == selectedEventIndex) {
            if (!scanPurposesReady) loadSelectedPurposes()
            setContextSheetOpen(false)
            return
        }
        presenter.cancelScan()
        selectedEventIndex = index
        scanNext()
        // The previous event's purposes must not survive the switch, not even for the frames before
        // the new list lands: [scanPurposesReady] keeps the decode path closed until it does.
        clearPurposesWhileLoading()
        lastSubmittedSignature = null
        bindSelectedEventHeader()
        loadSelectedPurposes()
        setContextSheetOpen(false)
        announceContext()
    }

    /**
     * The purposes of the event just left are dropped, and the field says what it is waiting for.
     * [showPurposes] is the only writer that can reopen the decode path.
     */
    private fun clearPurposesWhileLoading() {
        scanPurposesReady = false
        purposeOptions.clear()
        lastRejectedPayload = null
        contextPurpose.text = getString(R.string.manage_scan_purposes_loading)
        contextPurpose.visibility = View.VISIBLE
        purposeNote.text = ""
        bindPurposeNote()
        bindContextAffordance()
    }

    /** One sheet row (§5.13): title over subtitle, trailing 16dp check when current. */
    private fun buildContextRow(
        titleText: String,
        subtitleText: String,
        selected: Boolean,
        onCommit: () -> Unit,
    ): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dimenOffset(R.dimen.ui_touch_min)
        isSelected = selected
        setPadding(
            dimenOffset(R.dimen.ui_gutter),
            dimenOffset(R.dimen.space_md),
            dimenOffset(R.dimen.ui_gutter),
            dimenOffset(R.dimen.space_md),
        )
        setBackgroundColor(color(if (selected) R.color.ui_primary_wash else R.color.ui_surface))
        setOnClickListener { onCommit() }
        addView(LinearLayout(this@ScannerActivity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(this@ScannerActivity).apply {
                text = titleText
                setTextAppearance(R.style.TextAppearance_EventQR_Type_Title)
                setTextColor(color(R.color.ui_text_primary))
            })
            addView(TextView(this@ScannerActivity).apply {
                text = subtitleText
                setTextAppearance(R.style.TextAppearance_EventQR_Type_BodySmall)
                setTextColor(color(R.color.ui_text_secondary))
            })
        })
        addView(ImageView(this@ScannerActivity).apply {
            setImageResource(AppIcons.Check)
            ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(color(R.color.ui_primary)))
            contentDescription = null
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            visibility = if (selected) View.VISIBLE else View.GONE
            layoutParams = LinearLayout.LayoutParams(
                dimenOffset(R.dimen.ui_chevron),
                dimenOffset(R.dimen.ui_chevron),
            ).apply { marginStart = dimenOffset(R.dimen.space_8) }
        })
    }

    private fun announceContext() {
        contextBar.contentDescription?.let { contextBar.announceForAccessibility(it) }
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

    /**
     * Starts one scan for the live selection. Returns false when the scan was refused before it
     * left the screen (no purpose, duplicate window, bad input): the caller keeps the decoded
     * payload so the same pass in frame is answered once, not once per frame.
     */
    private fun submitCurrentSelection(): Boolean {
        val selectedEvent = selectedEvent() ?: return rejectScan(getString(R.string.scanner_no_assigned_event_selected))
        if (purposeOptions.isEmpty()) return rejectScan(getString(R.string.scanner_no_scan_purposes_enabled_for_this_ev))
        val purpose = selectedPurpose()
            ?: return rejectScan(getString(R.string.scanner_no_scan_purpose_selected))
        if (!isPurposeAllowed(purpose)) return rejectScan(getString(R.string.staff_print_id_permission_required))
        val qrValue = qrInput.text.toString().trim()
        // Only manual entry can reach here blank (the camera path rejects empty payloads first).
        if (qrValue.isBlank()) return rejectScan(getString(R.string.scanner_manual_entry_required))
        val signature = "${selectedEvent.id}|${purpose.scanPurposeId}|$qrValue"
        val now = SystemClock.elapsedRealtime()
        if (submitInFlight || (signature == lastSubmittedSignature && now - lastSubmittedAtMs < duplicateWindowMs)) {
            return rejectScan(getString(R.string.scanner_scan_is_already_being_processed_plea))
        }
        if (signature != lastSubmittedSignature) {
            // A distinct pass gets a distinct idempotency key; the same pass keeps the one already
            // minted, so a retry after a timeout is answered with the row the server stored. Reward
            // redemption posts to its own endpoint, which takes no key and must never be replayed
            // through the transaction path, so it mints no pending state at all.
            clearPendingScan()
            if (!isRewardRedemption(purpose)) {
                pendingRequestId = UUID.randomUUID()
                pendingQrValue = qrValue
                pendingEventId = selectedEvent.id
                pendingPurpose = purpose
            }
        }
        submitInFlight = true
        lastSubmittedSignature = signature
        lastSubmittedAtMs = now
        // The labels the result block restates. Read now, so a later selection change or a
        // rotation cannot relabel a finished scan (§6.3 v1.9).
        scanScope = ScanScope(selectedEvent.label, purpose.displayName())
        hideScanStrip()
        if (isRewardRedemption(purpose)) {
            presenter.submitRewardRedemptionScan(selectedEvent.id, purpose, qrValue, staffUserId)
            return true
        }
        val requestId = pendingRequestId ?: UUID.randomUUID().also { pendingRequestId = it }
        if (purpose.code == ScanPurposeCode.ID_PRINT) {
            presenter.verifyScan(selectedEvent.id, purpose, qrValue, staffUserId)
        } else {
            presenter.recordScan(selectedEvent.id, purpose, qrValue, staffUserId, requestId)
        }
        return true
    }

    private fun rejectScan(message: String): Boolean {
        showMessage(message)
        return false
    }

    private fun clearPendingScan() {
        pendingRequestId = null
        pendingQrValue = null
        pendingEventId = null
        pendingPurpose = null
    }

    /** A replay that can still be retried with its original idempotency key. */
    private fun hasPendingScan(): Boolean =
        pendingRequestId != null && pendingQrValue != null && pendingEventId != null && pendingPurpose != null

    private fun isRewardRedemption(purpose: ScanPurposeResponse): Boolean =
        purpose.code == ScanPurposeCode.REWARD_REDEMPTION_SCAN || purpose.code == ScanPurposeCode.REWARD_REDEMPTION

    private fun restorePendingScan(state: Bundle?) {
        if (state == null) return
        val scopeEvent = state.getString(STATE_SCOPE_EVENT)
        val scopePurpose = state.getString(STATE_SCOPE_PURPOSE)
        scanScope = if (!scopeEvent.isNullOrBlank() && !scopePurpose.isNullOrBlank()) {
            ScanScope(scopeEvent, scopePurpose)
        } else {
            null
        }
        lastSubmittedSignature = state.getString(STATE_LAST_SIGNATURE)
        lastSubmittedAtMs = state.getLong(STATE_LAST_SUBMITTED_AT, 0L)
        restorePrintContext(state)
        if (state.getBoolean(STATE_PRINTING, false)) {
            // The print coroutine died with the previous activity, so the outcome is unknown and
            // nothing is still in flight here: the restored panel offers the action again.
            Log.d(tag, "restored after an interrupted ID print; outcome unknown")
        }
        pendingRequestId = state.getString(STATE_PENDING_REQUEST_ID)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        pendingQrValue = state.getString(STATE_PENDING_QR)
        pendingEventId = state.getString(STATE_PENDING_EVENT_ID)
        pendingPurpose = runCatching {
            ScanPurposeResponse(
                scanPurposeId = UUID.fromString(state.getString(STATE_PENDING_PURPOSE_ID)),
                eventId = UUID.fromString(state.getString(STATE_PENDING_EVENT_ID)),
                name = state.getString(STATE_PENDING_PURPOSE_NAME).orEmpty(),
                code = ScanPurposeCode.valueOf(state.getString(STATE_PENDING_PURPOSE_CODE)!!),
                active = true,
                trackingOnly = false,
            )
        }.getOrNull()
    }

    // ── ID printing (verify, then print - not a transaction) ────────────────

    /** The verified pass the print call needs; null when the screen holds no printable pass. */
    private data class PrintContext(
        val eventId: String,
        val attendeeId: String,
        val attendeeName: String,
        val qrValue: String,
    )

    private fun printContext(): PrintContext? {
        val eventId = printEventId?.takeIf { it.isNotBlank() } ?: return null
        val attendeeId = printAttendeeId?.takeIf { it.isNotBlank() } ?: return null
        return PrintContext(eventId, attendeeId, printAttendeeName.orEmpty(), printQrValue.orEmpty())
    }

    private fun capturePrintContext(result: ScanVerificationResponse) {
        printEventId = result.eventId.toString()
        printAttendeeId = result.attendeeUserId.toString()
        printAttendeeName = result.attendeeName.orEmpty()
        printQrValue = result.qrValue
        // The scan scope was captured at submit time and survives rotation, so the name is there
        // even when this verification was a replay that beat the event list.
        printEventName = scanScope?.eventLabel?.takeIf { it.isNotBlank() } ?: selectedEvent()?.label
    }

    private fun clearPrintContext() {
        printEventId = null
        printAttendeeId = null
        printAttendeeName = null
        printQrValue = null
        printEventName = null
    }

    /** Restores the pass captured before a rotation, so the print panel keeps working. */
    private fun restorePrintContext(state: Bundle?) {
        if (state == null) return
        printEventId = state.getString(STATE_PRINT_EVENT_ID)
        printAttendeeId = state.getString(STATE_PRINT_ATTENDEE_ID)
        printAttendeeName = state.getString(STATE_PRINT_ATTENDEE_NAME)
        printQrValue = state.getString(STATE_PRINT_QR)
        printEventName = state.getString(STATE_PRINT_EVENT_NAME)
    }

    private fun printPendingId() {
        if (savingPrint) return
        val eventId = printEventId?.takeIf { it.isNotBlank() } ?: return
        val attendeeId = printAttendeeId?.takeIf { it.isNotBlank() } ?: return
        // Read the label captured with the verified pass; the spinner is only a fallback, because a
        // replay can finish before loadEvents repopulates the options.
        val eventName = printEventName?.takeIf { it.isNotBlank() }
            ?: eventOptions.firstOrNull { it.id == eventId }?.label.orEmpty()
        savingPrint = true
        resultPrimary.isEnabled = false
        resultPrimary.text = getString(R.string.ui_staff_result_printing)
        lifecycleScope.launch {
            // Template visible fields (default QR + name when the event has none saved) and attendee details
            var registrationNumber: Int? = null
            var role = ""
            var eventDate = ""

            val visibleFields = repository.getIdCardVisibleFields(eventId)
            val attendeeResult = repository.getAttendeeByEvent(eventId, attendeeId)
            if (attendeeResult is NetworkResult.Success) {
                registrationNumber = attendeeResult.data.registrationNumber
                role = attendeeResult.data.attendeeRole.orEmpty()
                eventDate = attendeeResult.data.eventStartAt?.let {
                    DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
                        .withZone(ZoneId.of("Asia/Manila")).format(it)
                }.orEmpty()
            }

            when (val result = repository.printAttendeeId(eventId, attendeeId)) {
                is NetworkResult.Success -> {
                    Log.d(tag, "printResult success=true eventId=$eventId")
                    val cardData = AndroidIdPrinter.CardData(
                        attendeeName = printAttendeeName.orEmpty(),
                        eventName = eventName,
                        registrationNumber = registrationNumber,
                        role = role,
                        eventDate = eventDate,
                        visibleFields = visibleFields,
                        qrValue = printQrValue.orEmpty(),
                    )
                    setScanNextPrimary()
                    openSystemPrintDialog(eventId, cardData, result.data.printLogId, result.data.message)
                }
                is NetworkResult.Error -> {
                    Log.w(tag, "printResult success=false eventId=$eventId")
                    bindPrintFailure(result)
                }
                NetworkResult.Loading -> Unit
            }
            savingPrint = false
        }
    }

    @VisibleForTesting
    internal open fun openSystemPrintDialog(
        eventId: String,
        cardData: AndroidIdPrinter.CardData,
        printLogId: UUID,
        serverMessage: String,
    ) {
        val printRepository = repository
        AndroidIdPrinter.print(
            this@ScannerActivity,
            "EventQR ID - ${cardData.attendeeName}",
            cardData,
        ) { outcome ->
            printRepository.confirmPrintLogsInBackground(eventId, listOf(printLogId), outcome)
            onPrintOutcome(outcome, serverMessage)
        }
    }

    /**
     * A failed ID-print request. A refusal - the permission can be revoked mid-session, so the
     * server answers 403 - gets the permission copy instead of the generic ID-print-failed panel.
     * Nothing is printed in either case.
     */
    @VisibleForTesting
    internal fun bindPrintFailure(result: NetworkResult.Error) {
        if (PrintPermission.isPermissionDenied(result)) {
            Toast.makeText(this, getString(R.string.staff_print_id_permission_required), Toast.LENGTH_LONG).show()
            setScanNextPrimary()
            return
        }
        bindPrintFailed(result.message)
    }

    @VisibleForTesting
    internal fun onPrintOutcome(outcome: AndroidIdPrinter.PrintOutcome, serverMessage: String) {
        if (isFinishing || isDestroyed) return
        when (outcome) {
            AndroidIdPrinter.PrintOutcome.SENT -> showPrintSuccessDialog(serverMessage)
            AndroidIdPrinter.PrintOutcome.CANCELLED ->
                showPrintNoticeDialog(getString(R.string.staff_print_cancelled_title), getString(R.string.staff_print_cancelled_message))
            AndroidIdPrinter.PrintOutcome.FAILED ->
                bindPrintFailed(getString(R.string.staff_print_job_failed_message))
        }
    }

    /** Print-specific failure: the pass was verified, so it must not read as a rejected scan. */
    @VisibleForTesting
    internal fun bindPrintFailed(reason: String) {
        bindResultBlock(
            tone = ResultTone.ERROR,
            state = getString(R.string.ui_staff_result_print_failed),
            hint = getString(R.string.ui_staff_result_print_failed_hint),
            attendee = printAttendeeName?.takeIf { it.isNotBlank() },
            registrationStatus = null,
            points = null,
            detail = reason.takeIf { it.isNotBlank() },
        )
        setScanNextPrimary()
        showResultGroup(true)
    }

    private fun showPrintNoticeDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(getString(R.string.staff_print_ok), null)
            .show()
    }

    private fun showPrintSuccessDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.staff_print_sent_title))
            .setMessage(message.ifBlank { getString(R.string.staff_print_sent_default_message) })
            .setCancelable(false)
            .setPositiveButton(getString(R.string.staff_print_done)) { _, _ -> scanNext() }
            .show()
    }

    // ── Reward redemption (separate screen; it is not a check-in) ───────────

    private fun openRewardRedemptionScanResult(result: RewardRedemptionScanResponse) {
        startActivity(Intent(this, RewardRedemptionScanResultActivity::class.java).apply {
            putExtra(StaffScreenExtras.EXTRA_EVENT_ID, result.eventId.toString())
            putExtra(StaffScreenExtras.EXTRA_ATTENDEE_ID, result.attendeeUserId.toString())
            putExtra(StaffScreenExtras.EXTRA_ATTENDEE_NAME, result.attendeeName.orEmpty())
            putExtra(StaffScreenExtras.EXTRA_ATTENDEE_EMAIL, result.attendeeEmail.orEmpty())
            putExtra(StaffScreenExtras.EXTRA_SCAN_PURPOSE_ID, result.scanPurposeId.toString())
            putExtra(AppRewardExtras.EXTRA_REDEMPTION_SCAN_LOG_ID, result.redemptionScanLogId.toString())
            putExtra(AppRewardExtras.EXTRA_POINTS_BALANCE, result.pointsBalance)
            putStringArrayListExtra(
                AppRewardExtras.EXTRA_ELIGIBLE_REWARD_IDS,
                ArrayList(result.eligibleRewards.map { it.rewardId.toString() }),
            )
            putExtra(StaffScreenExtras.EXTRA_STAFF_USER_ID, staffUserId.orEmpty())
            putExtra(StaffScreenExtras.EXTRA_SCAN_REJECTED, result.rejected)
            putExtra(StaffScreenExtras.EXTRA_SCAN_REJECTION_REASON, result.rejectionReason.orEmpty())
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
        const val STATE_SELECTED_EVENT_ID = "scanner_selected_event_id"
        const val STATE_PENDING_REQUEST_ID = "scanner_pending_request_id"
        const val STATE_PENDING_QR = "scanner_pending_qr"
        const val STATE_PENDING_EVENT_ID = "scanner_pending_event_id"
        const val STATE_PENDING_PURPOSE_ID = "scanner_pending_purpose_id"
        const val STATE_PENDING_PURPOSE_CODE = "scanner_pending_purpose_code"
        const val STATE_PENDING_PURPOSE_NAME = "scanner_pending_purpose_name"
        const val STATE_LAST_SIGNATURE = "scanner_last_signature"
        const val STATE_LAST_SUBMITTED_AT = "scanner_last_submitted_at"
        const val STATE_RESULT_TONE = "scanner_result_tone"
        const val STATE_RESULT_STATE = "scanner_result_state"
        const val STATE_RESULT_HINT = "scanner_result_hint"
        const val STATE_RESULT_ATTENDEE = "scanner_result_attendee"
        const val STATE_RESULT_REGISTRATION = "scanner_result_registration"
        const val STATE_RESULT_POINTS = "scanner_result_points"
        const val STATE_RESULT_DETAIL = "scanner_result_detail"
        const val STATE_PRINT_EVENT_ID = "scanner_print_event_id"
        const val STATE_PRINT_ATTENDEE_ID = "scanner_print_attendee_id"
        const val STATE_PRINT_ATTENDEE_NAME = "scanner_print_attendee_name"
        const val STATE_PRINT_QR = "scanner_print_qr"
        const val STATE_PRINT_EVENT_NAME = "scanner_print_event_name"
        const val STATE_PRINTING = "scanner_printing"
        const val NO_POINTS = -1
        const val BRACKET_FLASH_MS = 600L
        const val CONTEXT_SHEET_MAX_SCREEN_FRACTION = 0.6f
        val BRACKET_IDS = intArrayOf(
            R.id.scanBracketTl,
            R.id.scanBracketTr,
            R.id.scanBracketBl,
            R.id.scanBracketBr,
        )
    }
}
