package com.thedavelopers.eventqr.features.staff

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.Validators
import com.thedavelopers.eventqr.features.idprinting.IdPrintLogAdapter
import com.thedavelopers.eventqr.features.idprinting.model.dto.IdPrintRequest
import com.thedavelopers.eventqr.features.registrations.RegistrationAdapter
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse
import com.thedavelopers.eventqr.features.scanpurposes.model.dto.ScanPurposeResponse
import com.thedavelopers.eventqr.features.transactions.TransactionAdapter
import com.thedavelopers.eventqr.features.transactions.TransactionLogAdapter
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionRequest
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID

data class EventSpinnerOption(val id: String, val label: String)

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
        job = kotlinx.coroutines.MainScope().launch {
            when (val result = repository.getEvents()) {
                is NetworkResult.Success -> view?.showEvents(result.data.map { EventSpinnerOption(it.eventId.toString(), it.title) })
                is NetworkResult.Error -> view?.showMessage(result.message)
                NetworkResult.Loading -> Unit
            }
        }
    }

    fun loadPurposes(eventId: String) {
        job = kotlinx.coroutines.MainScope().launch {
            when (val result = repository.getScanPurposesByEvent(eventId)) {
                is NetworkResult.Success -> view?.showPurposes(result.data)
                is NetworkResult.Error -> view?.showMessage(result.message)
                NetworkResult.Loading -> Unit
            }
        }
    }

    fun submitScan(eventId: String, purposeId: String, qrValue: String, notes: String, staffUserId: String?) {
        if (!Validators.isNonEmpty(qrValue)) {
            view?.showMessage("QR value is required")
            return
        }
        job = kotlinx.coroutines.MainScope().launch {
            when (val result = repository.createTransaction(
                TransactionRequest(
                    eventId = UUID.fromString(eventId),
                    scanPurposeId = UUID.fromString(purposeId),
                    qrValue = qrValue.trim(),
                    staffUserId = staffUserId?.takeIf { it.isNotBlank() }?.let(UUID::fromString),
                    notes = notes.ifBlank { null },
                )
            )) {
                is NetworkResult.Success -> view?.appendScanResult(result.data)
                is NetworkResult.Error -> view?.showScanError(result.message)
                NetworkResult.Loading -> Unit
            }
        }
    }
}

interface ScannerContract {
    interface View {
        fun showEvents(items: List<EventSpinnerOption>)
        fun showPurposes(items: List<ScanPurposeResponse>)
        fun appendScanResult(result: TransactionResponse)
        fun showScanError(message: String)
        fun showMessage(message: String)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val sessionManager = SessionManager(this)
        if (sessionManager.getUserRole() != "STAFF") {
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

        eventSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: View?, position: Int, id: Long) {
                if (position in eventOptions.indices) {
                    presenter.loadPurposes(eventOptions[position].id)
                }
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>) = Unit
        }

        findViewById<Button>(R.id.btnSubmitScan).setOnClickListener {
            val eventId = eventOptions.getOrNull(eventSpinner.selectedItemPosition)?.id.orEmpty()
            val purposeId = purposeOptions.getOrNull(purposeSpinner.selectedItemPosition)?.scanPurposeId?.toString().orEmpty()
            presenter.submitScan(eventId, purposeId, qrInput.text.toString(), notesInput.text.toString(), staffUserId)
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
        if (items.isNotEmpty()) {
            presenter.loadPurposes(items.first().id)
        }
    }

    override fun showPurposes(items: List<ScanPurposeResponse>) {
        purposeOptions.clear()
        purposeOptions.addAll(items)
        val labels = items.map { it.name }
        purposeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        
        if (items.isNotEmpty()) {
            findViewById<Button>(R.id.btnSubmitScan)?.text = "Start Scanning"
        } else {
            findViewById<Button>(R.id.btnSubmitScan)?.text = "Select Scan Purpose First"
        }
    }

    override fun appendScanResult(result: TransactionResponse) {
        resultText.text = "${result.transactionResult.name} • ${result.transactionType.name} • ${result.reason ?: "Processed"}"
        adapter.submitItems(listOf(result) + (0 until adapter.itemCount).mapNotNull { null })
        Toast.makeText(this, "Scan recorded", Toast.LENGTH_SHORT).show()
    }

    override fun showScanError(message: String) {
        resultText.text = message
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
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
        job = kotlinx.coroutines.MainScope().launch {
            when (val result = repository.getTransactionsByEvent(eventId)) {
                is NetworkResult.Success -> view?.renderTransactions(result.data)
                is NetworkResult.Error -> view?.showMessage(result.message)
                NetworkResult.Loading -> Unit
            }
        }
    }
}

interface StaffTransactionsContract {
    interface View {
        fun renderTransactions(items: List<TransactionResponse>)
        fun showMessage(message: String)
    }
}

open class StaffTransactionsActivity : AppCompatActivity(), StaffTransactionsContract.View {
    private lateinit var presenter: StaffTransactionsPresenter
    private lateinit var adapter: TransactionLogAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionManager = SessionManager(this)
        if (sessionManager.getUserRole() != "STAFF") {
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
        
        findViewById<View>(R.id.btnBack)?.setOnClickListener { finish() }

        findViewById<Button>(R.id.btnLoadStaffTransactions).setOnClickListener {
            presenter.load(findViewById<EditText>(R.id.edtStaffTransactionsEventId).text.toString())
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
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
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
        }
    }

    fun loadLogs(eventId: String) {
        job = kotlinx.coroutines.MainScope().launch {
            when (val result = repository.getIdPrintsByEvent(eventId)) {
                is NetworkResult.Success -> view?.renderLogs(result.data)
                is NetworkResult.Error -> view?.showMessage(result.message)
                NetworkResult.Loading -> Unit
            }
        }
    }
}

interface IdPrintingContract {
    interface View {
        fun showPrintResult(message: String)
        fun renderLogs(items: List<com.thedavelopers.eventqr.features.idprinting.model.dto.IdPrintResponse>)
        fun showMessage(message: String)
    }
}

open class IdPrintingActivity : AppCompatActivity(), IdPrintingContract.View {
    private lateinit var presenter: IdPrintingPresenter
    private lateinit var adapter: IdPrintLogAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionManager = SessionManager(this)
        if (sessionManager.getUserRole() != "STAFF") {
            Toast.makeText(this, "Access Denied: Staff only", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContentView(R.layout.activity_id_printing)

        presenter = IdPrintingPresenter(this, StaffRepository(this))
        adapter = IdPrintLogAdapter()

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
        job = kotlinx.coroutines.MainScope().launch {
            when (val result = repository.getRegistrationsByEvent(eventId)) {
                is NetworkResult.Success -> view?.renderRegistrations(result.data)
                is NetworkResult.Error -> view?.showMessage(result.message)
                NetworkResult.Loading -> Unit
            }
        }
    }
}

interface EventRegistrationsContract {
    interface View {
        fun renderRegistrations(items: List<RegistrationResponse>)
        fun showMessage(message: String)
    }
}

open class EventRegistrationsActivity : AppCompatActivity(), EventRegistrationsContract.View {
    private lateinit var presenter: EventRegistrationsPresenter
    private lateinit var adapter: RegistrationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionManager = SessionManager(this)
        if (sessionManager.getUserRole() != "STAFF") {
            Toast.makeText(this, "Access Denied: Staff only", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContentView(R.layout.activity_event_registrations)

        presenter = EventRegistrationsPresenter(this, StaffRepository(this))
        adapter = RegistrationAdapter()

        findViewById<RecyclerView>(R.id.recyclerEventRegistrations).apply {
            layoutManager = LinearLayoutManager(this@EventRegistrationsActivity)
            adapter = this@EventRegistrationsActivity.adapter
        }

        findViewById<Button>(R.id.btnLoadEventRegistrations).setOnClickListener {
            presenter.load(findViewById<EditText>(R.id.edtRegistrationsEventId).text.toString())
        }
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }

    override fun renderRegistrations(items: List<RegistrationResponse>) {
        adapter.submitItems(items)
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
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
        job = kotlinx.coroutines.MainScope().launch {
            when (val result = repository.getEvents()) {
                is NetworkResult.Success -> {
                    view?.renderEvents(result.data.take(2))
                    // For demo, we just show some transactions as recent scans
                    if (result.data.isNotEmpty()) {
                        val firstEventId = result.data.first().eventId.toString()
                        when (val trans = repository.getTransactionsByEvent(firstEventId)) {
                            is NetworkResult.Success -> {
                                view?.renderRecentScans(trans.data.take(3))
                                view?.updateStats(trans.data.size, trans.data.count { it.transactionType.name == "CHECK_IN" })
                            }
                            else -> Unit
                        }
                    }
                }
                is NetworkResult.Error -> view?.showMessage(result.message)
                NetworkResult.Loading -> Unit
            }
        }
    }
}

interface StaffDashboardContract {
    interface View {
        fun renderEvents(items: List<com.thedavelopers.eventqr.features.events.model.dto.EventResponse>)
        fun renderRecentScans(items: List<TransactionResponse>)
        fun updateStats(scans: Int, checkins: Int)
        fun showMessage(message: String)
    }
}

open class StaffDashboardActivity : AppCompatActivity(), StaffDashboardContract.View {
    private lateinit var presenter: StaffDashboardPresenter
    private lateinit var adapter: TransactionLogAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionManager = SessionManager(this)
        if (sessionManager.getUserRole() != "STAFF") {
            Toast.makeText(this, "Access Denied: Staff only", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContentView(R.layout.activity_staff_dashboard)

        presenter = StaffDashboardPresenter(this, StaffRepository(this))
        adapter = TransactionLogAdapter()

        findViewById<RecyclerView>(R.id.recyclerRecentScans).apply {
            layoutManager = LinearLayoutManager(this@StaffDashboardActivity)
            adapter = this@StaffDashboardActivity.adapter
        }

        findViewById<TextView>(R.id.txtStaffName).text = sessionManager.getFullName() ?: "Dharell Dave"

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

    override fun renderEvents(items: List<com.thedavelopers.eventqr.features.events.model.dto.EventResponse>) {
        findViewById<TextView>(R.id.txtAssignedCount).text = items.size.toString()
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
}

open class StaffProfileActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val sessionManager = SessionManager(this)
        if (sessionManager.getUserRole() != "STAFF") {
            Toast.makeText(this, "Access Denied: Staff only", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContentView(R.layout.activity_profile)

        findViewById<TextView>(R.id.txtProfileName).text = sessionManager.getFullName() ?: "Staff User"
        findViewById<TextView>(R.id.txtProfileRole).text = "Staff"
        findViewById<TextView>(R.id.txtProfileEmail).text = sessionManager.getEmail() ?: "staff@eventqr.com"
        
        findViewById<Button>(R.id.btnProfileLogout).setOnClickListener {
            sessionManager.clearSession()
            startActivity(Intent(this, com.thedavelopers.eventqr.SignIn::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
        }

        findViewById<View>(R.id.navDashboard).setOnClickListener {
            startActivity(Intent(this, StaffDashboardActivity::class.java))
            finish()
        }
    }
}
