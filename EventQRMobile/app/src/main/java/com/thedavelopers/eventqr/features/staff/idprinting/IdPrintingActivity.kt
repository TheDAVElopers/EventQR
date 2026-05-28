package com.thedavelopers.eventqr.features.staff

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.features.idprinting.IdPrintLogAdapter
import com.thedavelopers.eventqr.features.idprinting.model.dto.IdPrintResponse

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

    override fun renderLogs(items: List<IdPrintResponse>) {
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
