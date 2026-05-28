package com.thedavelopers.eventqr.features.staff.scanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
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
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.features.staff.EventSpinnerOption
import com.thedavelopers.eventqr.features.staff.StaffDashboardActivity
import com.thedavelopers.eventqr.features.staff.StaffProfileActivity
import com.thedavelopers.eventqr.features.staff.StaffRepository
import com.thedavelopers.eventqr.features.staff.StaffTransactionsActivity
import com.thedavelopers.eventqr.features.staff.StaffScreenExtras
import com.thedavelopers.eventqr.features.staff.StaffCameraScannerActivity
import com.thedavelopers.eventqr.features.staff.model.dto.ScanVerificationResponse
import com.thedavelopers.eventqr.features.transactions.TransactionAdapter
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse
import com.thedavelopers.eventqr.features.scanpurposes.model.dto.ScanPurposeResponse
import kotlinx.coroutines.launch

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

        findViewById<Button>(R.id.btnSubmitScan).setOnClickListener {
            val selectedEvent = eventOptions.getOrNull(eventSpinner.selectedItemPosition)
            val selectedPurpose = purposeOptions.getOrNull(purposeSpinner.selectedItemPosition)
            if (selectedEvent == null || selectedPurpose == null) {
                showMessage("Select an event and scan purpose")
                return@setOnClickListener
            }
            presenter.submitScan(selectedEvent.id, selectedPurpose, qrInput.text.toString(), notesInput.text.toString(), staffUserId)
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
        eventSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        if (!preselectedEventId.isNullOrBlank()) {
            val index = items.indexOfFirst { it.id == preselectedEventId }
            if (index >= 0) eventSpinner.setSelection(index)
        }
        loadSelectedPurposes()
    }

    override fun showPurposes(items: List<ScanPurposeResponse>) {
        purposeOptions.clear()
        purposeOptions.addAll(items)
        purposeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items.map { it.name })
    }

    override fun appendScanResult(result: TransactionResponse) {
        adapter.submitItems(listOf(result))
    }

    override fun showVerificationResult(result: ScanVerificationResponse) {
        resultText.text = result.message
    }

    override fun showScanError(message: String) {
        resultText.text = message
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showLoading(isLoading: Boolean) {
        findViewById<View>(R.id.progressScanner).visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun loadSelectedPurposes() {
        val selectedEvent = eventOptions.getOrNull(eventSpinner.selectedItemPosition)
        if (selectedEvent != null) {
            presenter.loadPurposes(selectedEvent.id)
        }
    }

    private fun openCameraScanner() {
        cameraScanLauncher.launch(Intent(this, StaffCameraScannerActivity::class.java))
    }
}