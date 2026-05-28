package com.thedavelopers.eventqr.features.staff

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.core.util.Validators
import com.thedavelopers.eventqr.features.idprinting.IdPrintLogAdapter
import com.thedavelopers.eventqr.features.idprinting.model.dto.IdPrintRequest
import com.thedavelopers.eventqr.features.registrations.RegistrationAdapter
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse
import com.thedavelopers.eventqr.features.scanpurposes.model.dto.ScanPurposeResponse
import com.thedavelopers.eventqr.features.staff.model.dto.StaffAssignedEventResponse
import com.thedavelopers.eventqr.features.staff.model.dto.ScanVerificationResponse
import com.thedavelopers.eventqr.features.transactions.TransactionAdapter
import com.thedavelopers.eventqr.features.transactions.TransactionLogAdapter
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionRequest
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID

data class EventSpinnerOption(val id: String, val label: String, val canScan: Boolean)

class StaffEventAdapter(private val onEventClick: (StaffAssignedEventResponse) -> Unit) : RecyclerView.Adapter<StaffEventAdapter.ViewHolder>() {
    private val items = mutableListOf<StaffAssignedEventResponse>()

    fun submitItems(newItems: List<StaffAssignedEventResponse>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_staff_event, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.titleView.text = item.title
        holder.timeView.text = com.thedavelopers.eventqr.core.util.DateFormatters.formatInstant(item.eventStartAt)
        holder.itemView.setOnClickListener { onEventClick(item) }
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleView: TextView = itemView.findViewById(R.id.txtEventTitle)
        val timeView: TextView = itemView.findViewById(R.id.txtEventTime)
    }
}

class ScannerPresenter(
    private var view: ScannerContract.View?,
    private val repository: StaffRepository,
) {
    private var job: Job? = null

    fun detach() {
        job?.cancel()
        view = null
    }

    fun loadEvents() {
        view?.showLoading(true)
        job = kotlinx.coroutines.MainScope().launch {
            when (val result = repository.getEvents()) {
                is NetworkResult.Success -> view?.showEvents(result.data.map { EventSpinnerOption(it.eventId.toString(), it.title, it.canScan) })
                is NetworkResult.Error -> view?.showMessage(result.message)
                NetworkResult.Loading -> Unit
            }
            view?.showLoading(false)
        }
    }

    fun loadPurposes(eventId: String) {
        view?.showLoading(true)
        job = kotlinx.coroutines.MainScope().launch {
            when (val result = repository.getScanPurposesByEvent(eventId)) {
                is NetworkResult.Success -> view?.showPurposes(result.data)
                is NetworkResult.Error -> view?.showMessage(result.message)
                NetworkResult.Loading -> Unit
            }
            view?.showLoading(false)
        }
    }

    fun submitScan(eventId: String, purpose: ScanPurposeResponse, qrValue: String, notes: String, staffUserId: String?) {
        if (!Validators.isNonEmpty(eventId)) {
            view?.showMessage("Select an assigned event")
            return
        }
        if (!Validators.isNonEmpty(qrValue)) {
            view?.showMessage("QR value is required")
            return
        }
        view?.showLoading(true)
        job = kotlinx.coroutines.MainScope().launch {
            val request = TransactionRequest(
                eventId = UUID.fromString(eventId),
                scanPurposeId = purpose.scanPurposeId,
                qrValue = qrValue.trim(),
                staffUserId = staffUserId?.takeIf { it.isNotBlank() }?.let(UUID::fromString),
                notes = notes.ifBlank { null },
            )
            when (val result = repository.verifyScan(request)) {
                is NetworkResult.Success -> view?.showVerificationResult(result.data)
                is NetworkResult.Error -> view?.showScanError(result.message)
                NetworkResult.Loading -> Unit
            }
            view?.showLoading(false)
        }
    }
}

interface ScannerContract {
    interface View {
        fun showEvents(items: List<EventSpinnerOption>)
        fun showPurposes(items: List<ScanPurposeResponse>)
        fun appendScanResult(result: TransactionResponse)
        fun showVerificationResult(result: ScanVerificationResponse)
        fun showScanError(message: String)
        fun showMessage(message: String)
        fun showLoading(isLoading: Boolean)
    }
}

open class ScannerActivity : AppCompatActivity(), ScannerContract.View {
    private lateinit var presenter: ScannerPresenter
    private lateinit var eventSpinner: Spinner
    private lateinit var purposeSpinner: Spinner
    private lateinit var qrInput: EditText
    private lateinit var notesInput: EditText
    private lateinit var resultText: TextView
    private lateinit var adapter: TransactionAdapter
    private var staffUserId: String? = null

    private val eventOptions = mutableListOf<EventSpinnerOption>()
    private val purposeOptions = mutableListOf<ScanPurposeResponse>()
    private var preselectedEventId: String? = null
    private val cameraScanLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val scannedValue = result.data?.getStringExtra(StaffScreenExtras.EXTRA_QR_VALUE)
        if (result.resultCode == RESULT_OK && !scannedValue.isNullOrBlank()) {
            qrInput.setText(scannedValue)
            qrInput.setSelection(scannedValue.length)
        }
    }
    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            openCameraScanner()
        } else {
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
        adapter = TransactionAdapter()
        staffUserId = SessionManager(this).getUserId()

        findViewById<View>(R.id.navDashboard)?.setOnClickListener {
            startActivity(Intent(this, StaffDashboardActivity::class.java))
            finish()
        }

        findViewById<View>(R.id.navLogs)?.setOnClickListener {
            startActivity(Intent(this, StaffTransactionsActivity::class.java))
        }

        findViewById<View>(R.id.navProfile)?.setOnClickListener {
            startActivity(Intent(this, StaffProfileActivity::class.java))
        }

        findViewById<RecyclerView>(R.id.recyclerScannerResults).apply {
            layoutManager = LinearLayoutManager(this@ScannerActivity)
            adapter = this@ScannerActivity.adapter
        }

        findViewById<View>(R.id.layoutScannerPlaceholder)?.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                openCameraScanner()
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        eventSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: View?, position: Int, id: Long) {
                if (position in eventOptions.indices) {
                    val selectedEvent = eventOptions[position]
                    findViewById<TextView>(R.id.txtScannerSelectedEvent)?.apply {
                        text = selectedEvent.label
                        visibility = View.VISIBLE
                    }
                    presenter.loadPurposes(selectedEvent.id)
                    if (!selectedEvent.canScan) {
                        findViewById<Button>(R.id.btnSubmitScan).text = "No Scan QR access for this event"
                        findViewById<Button>(R.id.btnSubmitScan).isEnabled = false
                    }
                }
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>) = Unit
        }

        findViewById<Button>(R.id.btnSubmitScan).setOnClickListener {
            val selectedEvent = eventOptions.getOrNull(eventSpinner.selectedItemPosition)
            val eventId = selectedEvent?.id.orEmpty()
            val purpose = purposeOptions.getOrNull(purposeSpinner.selectedItemPosition)
            val qrValue = qrInput.text.toString().trim()
            if (eventId.isBlank()) {
                Toast.makeText(this, "Select an assigned event", Toast.LENGTH_SHORT).show()
            } else if (selectedEvent?.canScan != true) {
                Toast.makeText(this, "You do not have Scan QR access for this event", Toast.LENGTH_LONG).show()
            } else if (purpose == null) {
                Toast.makeText(this, "Select a scan purpose", Toast.LENGTH_SHORT).show()
            } else if (qrValue.isBlank()) {
                Toast.makeText(this, "QR value is required", Toast.LENGTH_SHORT).show()
            } else {
                presenter.submitScan(eventId, purpose, qrInput.text.toString(), notesInput.text.toString(), staffUserId)
            }
        }

        presenter.loadEvents()
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }

    override fun showEvents(items: List<EventSpinnerOption>) {
        eventOptions.clear()
        eventOptions.addAll(items)
        val labels = items.map { it.label }
        eventSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        findViewById<TextView>(R.id.txtScannerEmptyState).visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        findViewById<Spinner>(R.id.spnScannerEvent).visibility = if (items.size > 1) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.txtScannerSelectedEvent).visibility = if (items.size == 1) View.VISIBLE else View.GONE
        
        preselectedEventId?.let { id ->
            val index = items.indexOfFirst { it.id == id }
            if (index >= 0) {
                eventSpinner.setSelection(index)
                findViewById<TextView>(R.id.txtScannerSelectedEvent).text = items[index].label
                presenter.loadPurposes(id)
                return
            }
        }

        if (items.isNotEmpty()) {
            findViewById<TextView>(R.id.txtScannerSelectedEvent).text = items.first().label
            presenter.loadPurposes(items.first().id)
            if (!items.first().canScan) {
                findViewById<Button>(R.id.btnSubmitScan).text = "No Scan QR access for this event"
                findViewById<Button>(R.id.btnSubmitScan).isEnabled = false
                Toast.makeText(this, "Assigned to event, but scan permission is disabled", Toast.LENGTH_LONG).show()
            }
        } else {
            findViewById<Button>(R.id.btnSubmitScan).isEnabled = false
        }
    }

    private fun openCameraScanner() {
        val selected = eventOptions.getOrNull(eventSpinner.selectedItemPosition)
        if (selected != null && !selected.canScan) {
            Toast.makeText(this, "You do not have Scan QR access for this event", Toast.LENGTH_LONG).show()
            return
        }
        cameraScanLauncher.launch(Intent(this, StaffCameraScannerActivity::class.java))
    }

    override fun showPurposes(items: List<ScanPurposeResponse>) {
        purposeOptions.clear()
        purposeOptions.addAll(items)
        val labels = items.map { it.name }
        purposeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        val canScan = eventOptions.getOrNull(eventSpinner.selectedItemPosition)?.canScan == true
        
        if (items.isNotEmpty() && canScan) {
            findViewById<Button>(R.id.btnSubmitScan)?.text = "Verify QR"
            findViewById<Button>(R.id.btnSubmitScan)?.isEnabled = true
        } else if (items.isNotEmpty()) {
            findViewById<Button>(R.id.btnSubmitScan)?.text = "No Scan QR access for this event"
            findViewById<Button>(R.id.btnSubmitScan)?.isEnabled = false
        } else {
            findViewById<Button>(R.id.btnSubmitScan)?.text = "No scan purposes configured"
            findViewById<Button>(R.id.btnSubmitScan)?.isEnabled = false
            Toast.makeText(this, "No active scan purposes for this event. Contact organizer.", Toast.LENGTH_LONG).show()
        }
    }

    override fun appendScanResult(result: TransactionResponse) {
        startActivity(Intent(this, StaffTransactionResultActivity::class.java).apply {
            putExtra(StaffScreenExtras.EXTRA_EVENT_ID, result.eventId.toString())
            putExtra(StaffScreenExtras.EXTRA_EVENT_TITLE, result.eventTitle.orEmpty())
            putExtra(StaffScreenExtras.EXTRA_ATTENDEE_ID, result.attendeeUserId.toString())
            putExtra(StaffScreenExtras.EXTRA_ATTENDEE_NAME, result.attendeeName.orEmpty())
            putExtra(StaffScreenExtras.EXTRA_REGISTRATION_ID, result.registrationId.toString())
            putExtra(StaffScreenExtras.EXTRA_QR_CREDENTIAL_ID, result.qrCredentialId.toString())
            putExtra(StaffScreenExtras.EXTRA_TRANSACTION_ID, result.transactionId.toString())
            putExtra(StaffScreenExtras.EXTRA_TRANSACTION_RESULT, result.transactionResult.name)
            putExtra(StaffScreenExtras.EXTRA_TRANSACTION_TYPE, result.transactionType.name)
            putExtra(StaffScreenExtras.EXTRA_POINTS_DELTA, result.pointsDelta)
            putExtra(StaffScreenExtras.EXTRA_REASON, result.reason.orEmpty())
            putExtra(StaffScreenExtras.EXTRA_SCANNED_AT, result.scannedAt?.toString().orEmpty())
        })
    }

    override fun showVerificationResult(result: ScanVerificationResponse) {
        val event = eventOptions.getOrNull(eventSpinner.selectedItemPosition)
        val purpose = purposeOptions.getOrNull(purposeSpinner.selectedItemPosition)
        startActivity(Intent(this, StaffScanResultActivity::class.java).apply {
            putExtra(StaffScreenExtras.EXTRA_EVENT_ID, result.eventId.toString())
            putExtra(StaffScreenExtras.EXTRA_EVENT_TITLE, event?.label.orEmpty())
            putExtra(StaffScreenExtras.EXTRA_SCAN_PURPOSE_ID, result.scanPurposeId.toString())
            putExtra(StaffScreenExtras.EXTRA_SCAN_PURPOSE_NAME, purpose?.name.orEmpty())
            putExtra(StaffScreenExtras.EXTRA_SCAN_PURPOSE_CODE, result.scanPurposeCode.name)
            putExtra(StaffScreenExtras.EXTRA_QR_VALUE, result.qrValue)
            putExtra(StaffScreenExtras.EXTRA_STAFF_USER_ID, staffUserId.orEmpty())
            putExtra(StaffScreenExtras.EXTRA_IS_VALID, true)
            putExtra(StaffScreenExtras.EXTRA_MESSAGE, result.message.orEmpty())
            putExtra(StaffScreenExtras.EXTRA_ATTENDEE_ID, result.attendeeUserId.toString())
            putExtra(StaffScreenExtras.EXTRA_ATTENDEE_NAME, result.attendeeName.orEmpty())
            putExtra(StaffScreenExtras.EXTRA_ATTENDEE_EMAIL, result.attendeeEmail.orEmpty())
            putExtra(StaffScreenExtras.EXTRA_REGISTRATION_ID, result.registrationId.toString())
            putExtra(StaffScreenExtras.EXTRA_QR_CREDENTIAL_ID, result.qrCredentialId.toString())
            putExtra(StaffScreenExtras.EXTRA_REGISTRATION_STATUS, result.registrationStatus.name)
            putExtra(StaffScreenExtras.EXTRA_VERIFIED_AT, result.verifiedAt?.toString().orEmpty())
            putExtra(StaffScreenExtras.EXTRA_QR_ACTIVE, result.qrActive)
        })
    }

    override fun showScanError(message: String) {
        val event = eventOptions.getOrNull(eventSpinner.selectedItemPosition) ?: eventOptions.firstOrNull()
        val purpose = purposeOptions.getOrNull(purposeSpinner.selectedItemPosition)
        startActivity(Intent(this, StaffScanResultActivity::class.java).apply {
            putExtra(StaffScreenExtras.EXTRA_EVENT_ID, event?.id.orEmpty())
            putExtra(StaffScreenExtras.EXTRA_EVENT_TITLE, event?.label.orEmpty())
            putExtra(StaffScreenExtras.EXTRA_SCAN_PURPOSE_ID, purpose?.scanPurposeId?.toString().orEmpty())
            putExtra(StaffScreenExtras.EXTRA_SCAN_PURPOSE_NAME, purpose?.name.orEmpty())
            putExtra(StaffScreenExtras.EXTRA_SCAN_PURPOSE_CODE, purpose?.code?.name.orEmpty())
            putExtra(StaffScreenExtras.EXTRA_QR_VALUE, qrInput.text.toString())
            putExtra(StaffScreenExtras.EXTRA_STAFF_USER_ID, staffUserId.orEmpty())
            putExtra(StaffScreenExtras.EXTRA_IS_VALID, false)
            putExtra(StaffScreenExtras.EXTRA_MESSAGE, message)
        })
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showLoading(isLoading: Boolean) {
        findViewById<View>(R.id.progressScanner)?.visibility = if (isLoading) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnSubmitScan)?.isEnabled = !isLoading
    }
}

class StaffTransactionsPresenter(
    private var view: StaffTransactionsContract.View?,
    private val repository: StaffRepository,
) {
    private var job: Job? = null

    fun detach() {
        job?.cancel()
        view = null
    }

    fun load(eventId: String) {
        if (eventId.isBlank()) {
            view?.showMessage("Select an assigned event first")
            return
        }
        view?.showLoading(true)
        job = kotlinx.coroutines.MainScope().launch {
            when (val result = repository.getTransactionsByEvent(eventId)) {
                is NetworkResult.Success -> view?.renderTransactions(result.data)
                is NetworkResult.Error -> view?.showMessage(result.message)
                NetworkResult.Loading -> Unit
            }
            view?.showLoading(false)
        }
    }
}

interface StaffTransactionsContract {
    interface View {
        fun renderTransactions(items: List<TransactionResponse>)
        fun showMessage(message: String)
        fun showLoading(isLoading: Boolean)
    }
}

open class StaffTransactionsActivity : AppCompatActivity(), StaffTransactionsContract.View {
    private lateinit var presenter: StaffTransactionsPresenter
    private lateinit var adapter: TransactionLogAdapter
    private var selectedEventId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionManager = SessionManager(this)
        if (RoleMapper.normalizeRole(sessionManager.getUserRole()) != AccountRole.STAFF.name) {
            Toast.makeText(this, "Access Denied: Staff only", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContentView(R.layout.activity_staff_transaction_logs)

        presenter = StaffTransactionsPresenter(this, StaffRepository(this))
        adapter = TransactionLogAdapter()

        findViewById<RecyclerView>(R.id.recyclerStaffTransactions).apply {
            layoutManager = LinearLayoutManager(this@StaffTransactionsActivity)
            adapter = this@StaffTransactionsActivity.adapter
        }
        
        setupBottomNav()

        selectedEventId = intent.getStringExtra(StaffScreenExtras.EXTRA_EVENT_ID).orEmpty()

        if (selectedEventId.isNotBlank()) {
            findViewById<EditText>(R.id.edtStaffTransactionsEventId).setText(selectedEventId)
            presenter.load(selectedEventId)
        } else {
            kotlinx.coroutines.MainScope().launch {
                when (val eventsResult = StaffRepository(this@StaffTransactionsActivity).getEvents()) {
                    is NetworkResult.Success -> {
                        val firstEvent = eventsResult.data.firstOrNull()
                        if (firstEvent == null) {
                            findViewById<TextView>(R.id.txtStaffTransactionsEmptyState).visibility = View.VISIBLE
                            showMessage("No assigned events found")
                            return@launch
                        }
                        selectedEventId = firstEvent.eventId.toString()
                        findViewById<EditText>(R.id.edtStaffTransactionsEventId).setText(selectedEventId)
                        presenter.load(selectedEventId)
                    }
                    is NetworkResult.Error -> showMessage(eventsResult.message)
                    NetworkResult.Loading -> Unit
                }
            }
        }

        findViewById<Button>(R.id.btnLoadStaffTransactions).setOnClickListener {
            selectedEventId = findViewById<EditText>(R.id.edtStaffTransactionsEventId).text.toString()
            presenter.load(selectedEventId)
        }
    }

    private fun setupBottomNav() {
        findViewById<View>(R.id.navDashboard)?.setOnClickListener {
            startActivity(Intent(this, StaffDashboardActivity::class.java))
            finish()
        }

        findViewById<View>(R.id.navScanner)?.setOnClickListener {
            startActivity(Intent(this, ScannerActivity::class.java))
            finish()
        }

        findViewById<View>(R.id.navProfile)?.setOnClickListener {
            startActivity(Intent(this, StaffProfileActivity::class.java))
            finish()
        }
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }

    override fun renderTransactions(items: List<TransactionResponse>) {
        adapter.submitItems(items)
        findViewById<TextView>(R.id.txtTotalScans).text = items.size.toString()
        findViewById<TextView>(R.id.txtSuccessfulScans).text = items.count { it.transactionResult.name == "APPROVED" || it.transactionResult.name == "SUCCESS" }.toString()
        findViewById<TextView>(R.id.txtRejectedScans).text = items.count { it.transactionResult.name != "APPROVED" && it.transactionResult.name != "SUCCESS" }.toString()
        findViewById<TextView>(R.id.txtStaffTransactionsEmptyState).visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        findViewById<RecyclerView>(R.id.recyclerStaffTransactions).visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showLoading(isLoading: Boolean) {
        findViewById<View>(R.id.progressScanner)?.visibility = if (isLoading) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnLoadStaffTransactions)?.isEnabled = !isLoading
    }
}

class IdPrintingPresenter(
    private var view: IdPrintingContract.View?,
    private val repository: StaffRepository,
) {
    private var job: Job? = null

    fun detach() {
        job?.cancel()
        view = null
    }

    fun print(eventId: String, qrCredentialId: String, staffUserId: String, reprint: Boolean) {
        if (!Validators.isNonEmpty(eventId) || !Validators.isNonEmpty(qrCredentialId) || !Validators.isNonEmpty(staffUserId)) {
            view?.showMessage("Event ID, QR credential ID, and staff ID are required")
            return
        }
        view?.showLoading(true)
        job = kotlinx.coroutines.MainScope().launch {
            when (val result = repository.printId(
                IdPrintRequest(
                    eventId = UUID.fromString(eventId),
                    qrCredentialId = UUID.fromString(qrCredentialId),
                    staffUserId = UUID.fromString(staffUserId),
                    reprint = reprint,
                )
            )) {
                is NetworkResult.Success -> view?.showPrintResult(result.data.message)
                is NetworkResult.Error -> view?.showMessage(result.message)
                NetworkResult.Loading -> Unit
            }
            view?.showLoading(false)
        }
    }

    fun loadLogs(eventId: String) {
        view?.showLoading(true)
        job = kotlinx.coroutines.MainScope().launch {
            when (val result = repository.getIdPrintsByEvent(eventId)) {
                is NetworkResult.Success -> view?.renderLogs(result.data)
                is NetworkResult.Error -> view?.showMessage(result.message)
                NetworkResult.Loading -> Unit
            }
            view?.showLoading(false)
        }
    }
}

interface IdPrintingContract {
    interface View {
        fun showPrintResult(message: String)
        fun renderLogs(items: List<com.thedavelopers.eventqr.features.idprinting.model.dto.IdPrintResponse>)
        fun showMessage(message: String)
        fun showLoading(isLoading: Boolean)
    }
}

open class IdPrintingActivity : AppCompatActivity(), IdPrintingContract.View {
    private lateinit var presenter: IdPrintingPresenter
    private lateinit var adapter: IdPrintLogAdapter
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager(this)
        if (RoleMapper.normalizeRole(sessionManager.getUserRole()) != AccountRole.STAFF.name) {
            Toast.makeText(this, "Access Denied: Staff only", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContentView(R.layout.activity_id_printing)

        presenter = IdPrintingPresenter(this, StaffRepository(this))
        adapter = IdPrintLogAdapter()

        val preselectedEventId = intent.getStringExtra(StaffScreenExtras.EXTRA_EVENT_ID).orEmpty()
        if (preselectedEventId.isNotBlank()) {
            findViewById<EditText>(R.id.edtPrintEventId).setText(preselectedEventId)
            presenter.loadLogs(preselectedEventId)
        }
        sessionManager.getUserId()?.takeIf { it.isNotBlank() }?.let { userId ->
            findViewById<EditText>(R.id.edtPrintStaffUserId).setText(userId)
        }

        findViewById<RecyclerView>(R.id.recyclerIdPrintLogs).apply {
            layoutManager = LinearLayoutManager(this@IdPrintingActivity)
            adapter = this@IdPrintingActivity.adapter
        }

        findViewById<Button>(R.id.btnPrintId).setOnClickListener {
            presenter.print(
                findViewById<EditText>(R.id.edtPrintEventId).text.toString(),
                findViewById<EditText>(R.id.edtPrintQrCredentialId).text.toString(),
                findViewById<EditText>(R.id.edtPrintStaffUserId).text.toString(),
                findViewById<CheckBox>(R.id.chkReprint).isChecked,
            )
        }

        findViewById<Button>(R.id.btnLoadPrintLogs).setOnClickListener {
            presenter.loadLogs(findViewById<EditText>(R.id.edtPrintEventId).text.toString())
        }
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }

    override fun showPrintResult(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun renderLogs(items: List<com.thedavelopers.eventqr.features.idprinting.model.dto.IdPrintResponse>) {
        adapter.submitItems(items)
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showLoading(isLoading: Boolean) {
        findViewById<View>(R.id.progressScanner)?.visibility = if (isLoading) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnPrintId)?.isEnabled = !isLoading
    }
}

class EventRegistrationsPresenter(
    private var view: EventRegistrationsContract.View?,
    private val repository: StaffRepository,
) {
    private var job: Job? = null

    fun detach() {
        job?.cancel()
        view = null
    }

    fun load(eventId: String) {
        if (eventId.isBlank()) {
            view?.showMessage("Select an assigned event first")
            return
        }
        view?.showLoading(true)
        job = kotlinx.coroutines.MainScope().launch {
            when (val result = repository.getRegistrationsByEvent(eventId)) {
                is NetworkResult.Success -> view?.renderRegistrations(result.data)
                is NetworkResult.Error -> view?.showMessage(result.message)
                NetworkResult.Loading -> Unit
            }
            view?.showLoading(false)
        }
    }
}

interface EventRegistrationsContract {
    interface View {
        fun renderRegistrations(items: List<RegistrationResponse>)
        fun showMessage(message: String)
        fun showLoading(isLoading: Boolean)
    }
}

open class EventRegistrationsActivity : AppCompatActivity(), EventRegistrationsContract.View {
    private lateinit var presenter: EventRegistrationsPresenter
    private lateinit var adapter: RegistrationAdapter
    private var selectedEventId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionManager = SessionManager(this)
        if (RoleMapper.normalizeRole(sessionManager.getUserRole()) != AccountRole.STAFF.name) {
            Toast.makeText(this, "Access Denied: Staff only", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContentView(R.layout.activity_event_registrations)

        presenter = EventRegistrationsPresenter(this, StaffRepository(this))
        adapter = RegistrationAdapter { registration ->
            startActivity(Intent(this, StaffAttendeeDetailsActivity::class.java).apply {
                putExtra(StaffScreenExtras.EXTRA_EVENT_ID, registration.eventId.toString())
                putExtra(StaffScreenExtras.EXTRA_ATTENDEE_ID, registration.attendeeUserId.toString())
                putExtra(StaffScreenExtras.EXTRA_REGISTRATION_ID, registration.registrationId.toString())
                putExtra(StaffScreenExtras.EXTRA_QR_CREDENTIAL_ID, registration.qrCredentialId?.toString().orEmpty())
                putExtra(StaffScreenExtras.EXTRA_ATTENDEE_NAME, registration.attendeeName)
                putExtra(StaffScreenExtras.EXTRA_ATTENDEE_EMAIL, registration.attendeeEmail)
                putExtra(StaffScreenExtras.EXTRA_EVENT_TITLE, registration.eventTitle.orEmpty())
            })
        }

        findViewById<RecyclerView>(R.id.recyclerEventRegistrations).apply {
            layoutManager = LinearLayoutManager(this@EventRegistrationsActivity)
            adapter = this@EventRegistrationsActivity.adapter
        }

        selectedEventId = intent.getStringExtra(StaffScreenExtras.EXTRA_EVENT_ID).orEmpty()
        if (selectedEventId.isNotBlank()) {
            findViewById<EditText>(R.id.edtRegistrationsEventId).setText(selectedEventId)
            presenter.load(selectedEventId)
        } else {
            kotlinx.coroutines.MainScope().launch {
                when (val eventsResult = StaffRepository(this@EventRegistrationsActivity).getEvents()) {
                    is NetworkResult.Success -> {
                        val firstEvent = eventsResult.data.firstOrNull()
                        if (firstEvent == null) {
                            showMessage("No assigned events found")
                            return@launch
                        }
                        selectedEventId = firstEvent.eventId.toString()
                        findViewById<EditText>(R.id.edtRegistrationsEventId).setText(selectedEventId)
                        presenter.load(selectedEventId)
                    }
                    is NetworkResult.Error -> showMessage(eventsResult.message)
                    NetworkResult.Loading -> Unit
                }
            }
        }

        findViewById<Button>(R.id.btnLoadEventRegistrations).setOnClickListener {
            selectedEventId = findViewById<EditText>(R.id.edtRegistrationsEventId).text.toString()
            presenter.load(selectedEventId)
        }
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }

    override fun renderRegistrations(items: List<RegistrationResponse>) {
        adapter.submitItems(items)
        findViewById<RecyclerView>(R.id.recyclerEventRegistrations).visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showLoading(isLoading: Boolean) {
        findViewById<View>(R.id.progressScanner)?.visibility = if (isLoading) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnLoadEventRegistrations)?.isEnabled = !isLoading
    }
}

class StaffDashboardPresenter(
    private var view: StaffDashboardContract.View?,
    private val repository: StaffRepository,
) {
    private var job: Job? = null

    fun detach() {
        job?.cancel()
        view = null
    }

    fun loadData() {
        view?.showLoading(true)
        job = kotlinx.coroutines.MainScope().launch {
            when (val result = repository.getEvents()) {
                is NetworkResult.Success -> {
                    view?.renderEvents(result.data)
                    if (result.data.isEmpty()) {
                        view?.renderRecentScans(emptyList())
                        view?.updateStats(0, 0)
                    } else {
                        val recentTransactions = mutableListOf<TransactionResponse>()
                        for (event in result.data) {
                            when (val trans = repository.getTransactionsByEvent(event.eventId.toString())) {
                                is NetworkResult.Success -> recentTransactions.addAll(trans.data)
                                is NetworkResult.Error -> Unit
                                NetworkResult.Loading -> Unit
                            }
                        }
                        val sortedTransactions = recentTransactions.sortedByDescending { it.scannedAt ?: java.time.Instant.EPOCH }
                        view?.renderRecentScans(sortedTransactions.take(5))
                        view?.updateStats(sortedTransactions.size, sortedTransactions.count { it.transactionType.name == "ENTRY" || it.transactionType.name == "ATTENDANCE" })
                    }
                }
                is NetworkResult.Error -> view?.showMessage(result.message)
                NetworkResult.Loading -> Unit
            }
            view?.showLoading(false)
        }
    }
}

interface StaffDashboardContract {
    interface View {
        fun renderEvents(items: List<StaffAssignedEventResponse>)
        fun renderRecentScans(items: List<TransactionResponse>)
        fun updateStats(scans: Int, checkins: Int)
        fun showMessage(message: String)
        fun showLoading(isLoading: Boolean)
    }
}

open class StaffDashboardActivity : AppCompatActivity(), StaffDashboardContract.View {
    private lateinit var presenter: StaffDashboardPresenter
    private lateinit var adapter: TransactionLogAdapter
    private lateinit var eventAdapter: StaffEventAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionManager = SessionManager(this)
        if (RoleMapper.normalizeRole(sessionManager.getUserRole()) != AccountRole.STAFF.name) {
            Toast.makeText(this, "Access Denied: Staff only", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContentView(R.layout.activity_staff_dashboard)

        presenter = StaffDashboardPresenter(this, StaffRepository(this))
        adapter = TransactionLogAdapter()
        eventAdapter = StaffEventAdapter { event ->
            val intent = Intent(this, ScannerActivity::class.java)
            intent.putExtra(StaffScreenExtras.EXTRA_EVENT_ID, event.eventId.toString())
            startActivity(intent)
        }

        findViewById<RecyclerView>(R.id.recyclerRecentScans).apply {
            layoutManager = LinearLayoutManager(this@StaffDashboardActivity)
            adapter = this@StaffDashboardActivity.adapter
        }

        findViewById<RecyclerView>(R.id.recyclerAssignedEvents).apply {
            layoutManager = LinearLayoutManager(this@StaffDashboardActivity)
            adapter = eventAdapter
        }

        findViewById<TextView>(R.id.txtStaffName).text = sessionManager.getFullName() ?: sessionManager.getEmail() ?: "Staff User"
        findViewById<TextView>(R.id.txtStaffEmail).text = sessionManager.getEmail() ?: ""

        findViewById<View>(R.id.btnQuickScan).setOnClickListener {
            startActivity(Intent(this, ScannerActivity::class.java))
        }

        findViewById<View>(R.id.btnQuickRegistrations).setOnClickListener {
            startActivity(Intent(this, EventRegistrationsActivity::class.java))
        }

        findViewById<View>(R.id.btnQuickTransactions).setOnClickListener {
            startActivity(Intent(this, StaffTransactionsActivity::class.java))
        }

        findViewById<View>(R.id.btnQuickIdPrinting).setOnClickListener {
            startActivity(Intent(this, IdPrintingActivity::class.java))
        }

        findViewById<View>(R.id.txtScansToday).setOnClickListener {
            startActivity(Intent(this, ScannerActivity::class.java))
        }

        findViewById<View>(R.id.txtCheckinsToday).setOnClickListener {
            startActivity(Intent(this, EventRegistrationsActivity::class.java))
        }

        findViewById<View>(R.id.navScanner).setOnClickListener {
            startActivity(Intent(this, ScannerActivity::class.java))
        }

        findViewById<View>(R.id.navLogs).setOnClickListener {
            startActivity(Intent(this, StaffTransactionsActivity::class.java))
        }

        findViewById<View>(R.id.navProfile).setOnClickListener {
            startActivity(Intent(this, StaffProfileActivity::class.java))
        }

        presenter.loadData()
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }

    override fun renderEvents(items: List<StaffAssignedEventResponse>) {
        findViewById<TextView>(R.id.txtAssignedCount).text = items.size.toString()
        eventAdapter.submitItems(items)
        findViewById<TextView>(R.id.txtAssignedEmptyState).visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        findViewById<RecyclerView>(R.id.recyclerAssignedEvents).visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        findViewById<View>(R.id.btnQuickScan).isEnabled = items.any { it.canScan }
        
        if (items.isEmpty()) {
            Toast.makeText(this, "No events assigned to you yet", Toast.LENGTH_LONG).show()
        } else if (items.none { it.canScan }) {
            Toast.makeText(this, "No active Scan QR permission for assigned events", Toast.LENGTH_LONG).show()
        }
    }

    override fun renderRecentScans(items: List<TransactionResponse>) {
        adapter.submitItems(items)
    }

    override fun updateStats(scans: Int, checkins: Int) {
        // Find by parent to avoid ID collision if Scans Today is also a TextView ID
        findViewById<TextView>(R.id.txtScansToday).text = scans.toString()
        findViewById<TextView>(R.id.txtCheckinsToday).text = checkins.toString()
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showLoading(isLoading: Boolean) {
        findViewById<View>(R.id.progressScanner)?.visibility = if (isLoading) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnQuickScan)?.isEnabled = !isLoading
    }
}

open class StaffProfileActivity : AppCompatActivity() {
    private lateinit var sessionManager: SessionManager
    private lateinit var repository: com.thedavelopers.eventqr.features.attendee.AttendeeRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        sessionManager = SessionManager(this)
        repository = com.thedavelopers.eventqr.features.attendee.AttendeeRepository(this)
        
        if (RoleMapper.normalizeRole(sessionManager.getUserRole()) != AccountRole.STAFF.name) {
            Toast.makeText(this, "Access Denied: Staff only", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContentView(R.layout.activity_profile)
        
        setupStaffBottomNav()

        findViewById<Button>(R.id.btnProfileLogout).setOnClickListener {
            sessionManager.clearSession()
            startActivity(Intent(this, com.thedavelopers.eventqr.SignIn::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
        }

        findViewById<Button>(R.id.btnEditProfile)?.setOnClickListener {
            startActivity(Intent(this, com.thedavelopers.eventqr.features.attendee.AttendeeEditProfileActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        loadProfile()
    }

    private fun loadProfile() {
        renderProfile()
        
        kotlinx.coroutines.MainScope().launch {
            when (val result = repository.getMyProfile()) {
                is NetworkResult.Success -> {
                    val user = result.data
                    sessionManager.updateProfile(user.fullName, user.phoneNumber)
                    renderProfile()
                }
                else -> Unit
            }
        }
    }

    private fun renderProfile() {
        findViewById<TextView>(R.id.txtProfileName).text = sessionManager.getFullName() ?: "Staff User"
        findViewById<TextView>(R.id.txtProfileRole).text = RoleMapper.getDisplayName(sessionManager.getUserRole())
        findViewById<TextView>(R.id.txtProfileEmail).text = sessionManager.getEmail() ?: "staff@eventqr.com"
        findViewById<TextView>(R.id.txtPhone).text = sessionManager.getPhone() ?: "N/A"
    }

    private fun setupStaffBottomNav() {
        // Item 1: Dashboard
        findViewById<ImageView>(R.id.imgNavDashboard)?.apply {
            setImageResource(R.drawable.ic_nav_home)
            background = getDrawable(R.drawable.bg_nav_icon_inactive)
            imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.BLACK)
        }
        findViewById<TextView>(R.id.txtNavDashboard)?.apply {
            text = "Dashboard"
            setTypeface(null, android.graphics.Typeface.NORMAL)
        }
        findViewById<View>(R.id.navDashboard)?.setOnClickListener {
            startActivity(Intent(this, StaffDashboardActivity::class.java))
            finish()
        }

        // Item 2: Scan QR
        findViewById<ImageView>(R.id.imgNavEvents)?.apply {
            setImageResource(R.drawable.ic_qr_scan)
            background = getDrawable(R.drawable.bg_nav_icon_inactive)
            imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.BLACK)
        }
        findViewById<TextView>(R.id.txtNavEvents)?.apply {
            text = "Scan QR"
            setTypeface(null, android.graphics.Typeface.NORMAL)
        }
        findViewById<View>(R.id.navEvents)?.setOnClickListener {
            startActivity(Intent(this, ScannerActivity::class.java))
            finish()
        }

        // Item 3: Logs
        findViewById<ImageView>(R.id.imgNavRewards)?.apply {
            setImageResource(R.drawable.ic_file)
            background = getDrawable(R.drawable.bg_nav_icon_inactive)
            imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.BLACK)
        }
        findViewById<TextView>(R.id.txtNavRewards)?.apply {
            text = "Logs"
            setTypeface(null, android.graphics.Typeface.NORMAL)
        }
        findViewById<View>(R.id.navRewards)?.setOnClickListener {
            startActivity(Intent(this, StaffTransactionsActivity::class.java))
            finish()
        }

        // Item 4: Profile (Active)
        findViewById<ImageView>(R.id.imgNavProfile)?.apply {
            setImageResource(R.drawable.ic_nav_profile)
            background = getDrawable(R.drawable.bg_nav_icon_active)
            imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
        }
        findViewById<TextView>(R.id.txtNavProfile)?.apply {
            text = "Profile"
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        // No click listener needed for current page or just keep it
    }
}
