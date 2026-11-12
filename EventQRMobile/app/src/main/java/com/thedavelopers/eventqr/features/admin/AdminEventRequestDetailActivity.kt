package com.thedavelopers.eventqr.features.admin

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.thedavelopers.eventqr.ui.components.setContentWithDetailHeader
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.features.events.EventRequestDetailScreen
import com.thedavelopers.eventqr.features.events.model.dto.EventRequestResponse
import com.thedavelopers.eventqr.ui.theme.EventQrTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import com.thedavelopers.eventqr.R

class AdminEventRequestDetailActivity : AppCompatActivity() {

    private lateinit var repository: AdminRepository
    private var requestId: String = ""
    private var hasResumedOnce = false

    private val _request = MutableStateFlow<EventRequestResponse?>(null)
    private val _isLoading = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _decisionMessage = MutableStateFlow<String?>(null)
    private val _decisionIsError = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestId = intent.getStringExtra(EXTRA_REQUEST_ID).orEmpty()
        if (requestId.isBlank()) {
            Toast.makeText(this, this.getString(R.string.admin_event_request_detail_request_not_found), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        repository = AdminRepository(this)

        setContentWithDetailHeader(getString(R.string.ui_request_detail_title)) {
            EventQrTheme {
                val request = _request.collectAsStateWithLifecycle().value
                val isLoading = _isLoading.collectAsStateWithLifecycle().value
                val errorMessage = _errorMessage.collectAsStateWithLifecycle().value
                val decisionMessage = _decisionMessage.collectAsStateWithLifecycle().value
                val decisionIsError = _decisionIsError.collectAsStateWithLifecycle().value

                EventRequestDetailScreen(
                    request = request,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    isAdmin = true,
                    onRetryClick = { verifyAdminAndLoad() },
                    onApproveClick = { remarks -> approveRequest(remarks) },
                    onRejectClick = { remarks -> rejectRequest(remarks) },
                    decisionMessage = decisionMessage,
                    decisionIsError = decisionIsError,
                )
            }
        }

        verifyAdminAndLoad()
    }

    override fun onResume() {
        super.onResume()
        if (!hasResumedOnce) {
            hasResumedOnce = true
            return
        }
        if (requestId.isNotBlank()) {
            verifyAdminAndLoad()
        }
    }

    private fun verifyAdminAndLoad() {
        _isLoading.value = true
        _errorMessage.value = null
        _decisionMessage.value = null
        _decisionIsError.value = false

        lifecycleScope.launch {
            when (val result = repository.getCurrentUser()) {
                is NetworkResult.Success -> {
                    if (result.data.role != AccountRole.ADMIN && result.data.role != AccountRole.SUPER_ADMIN) {
                        _isLoading.value = false
                        _errorMessage.value = getString(R.string.ui_admin_error_admin_required)
                    } else {
                        loadRequest()
                    }
                }
                is NetworkResult.Error -> {
                    _isLoading.value = false
                    _errorMessage.value = toFriendlyError(result.message)
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun loadRequest() {
        _isLoading.value = true
        _errorMessage.value = null

        lifecycleScope.launch {
            when (val result = repository.getEventRequest(requestId)) {
                is NetworkResult.Success -> {
                    _request.value = result.data
                    _isLoading.value = false
                }
                is NetworkResult.Error -> {
                    _isLoading.value = false
                    _errorMessage.value = toFriendlyError(result.message)
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun approveRequest(remarks: String?) {
        _isLoading.value = true
        _decisionMessage.value = null
        lifecycleScope.launch {
            when (val result = repository.approveEvent(requestId, remarks)) {
                is NetworkResult.Success -> {
                    _decisionIsError.value = false
                    _decisionMessage.value = getString(R.string.ui_request_detail_approved)
                    setResult(RESULT_OK)
                    loadRequest()
                }
                is NetworkResult.Error -> {
                    _isLoading.value = false
                    _decisionIsError.value = true
                    _decisionMessage.value = toFriendlyError(result.message)
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun rejectRequest(remarks: String?) {
        _isLoading.value = true
        _decisionMessage.value = null
        lifecycleScope.launch {
            when (val result = repository.rejectEvent(requestId, remarks)) {
                is NetworkResult.Success -> {
                    _decisionIsError.value = false
                    _decisionMessage.value = getString(R.string.ui_request_detail_rejected)
                    setResult(RESULT_OK)
                    loadRequest()
                }
                is NetworkResult.Error -> {
                    _isLoading.value = false
                    _decisionIsError.value = true
                    _decisionMessage.value = toFriendlyError(result.message)
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun toFriendlyError(message: String): String {
        val normalized = message.lowercase()
        return when {
            normalized.contains("disabled") || normalized.contains("suspend") -> getString(R.string.ui_admin_error_disabled)
            normalized.contains("401") || normalized.contains("unauthorized") -> getString(R.string.ui_admin_error_session)
            normalized.contains("403") || normalized.contains("forbidden") || normalized.contains("admin access") -> getString(R.string.ui_admin_error_admin_required)
            normalized.contains("404") || normalized.contains("not found") -> getString(R.string.ui_admin_error_not_found)
            normalized.contains("409") || normalized.contains("conflict") -> getString(R.string.ui_admin_error_conflict)
            normalized.contains("400") || normalized.contains("invalid") || normalized.contains("bad request") -> getString(R.string.ui_admin_error_action_state)
            normalized.contains("500") -> getString(R.string.ui_admin_error_server)
            normalized.contains("unable to resolve host") || normalized.contains("failed to connect") || normalized.contains("timeout") -> getString(R.string.ui_admin_error_network)
            else -> message
        }
    }

    companion object {
        const val EXTRA_REQUEST_ID = "extra_request_id"
    }
}
