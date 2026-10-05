package com.thedavelopers.eventqr.features.admin

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.features.events.EventRequestDetailScreen
import com.thedavelopers.eventqr.features.events.model.dto.EventRequestResponse
import com.thedavelopers.eventqr.ui.theme.EventQrTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class AdminEventRequestDetailActivity : AppCompatActivity() {

    private lateinit var repository: AdminRepository
    private var requestId: String = ""

    private val _request = MutableStateFlow<EventRequestResponse?>(null)
    private val _isLoading = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestId = intent.getStringExtra(EXTRA_REQUEST_ID).orEmpty()
        if (requestId.isBlank()) {
            Toast.makeText(this, "Request not found.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        repository = AdminRepository(this)

        setContent {
            EventQrTheme {
                val request = _request.collectAsStateWithLifecycle().value
                val isLoading = _isLoading.collectAsStateWithLifecycle().value
                val errorMessage = _errorMessage.collectAsStateWithLifecycle().value

                EventRequestDetailScreen(
                    request = request,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    isAdmin = true,
                    onBackClick = { finish() },
                    onRetryClick = { verifyAdminAndLoad() },
                    onApproveClick = { remarks -> approveRequest(remarks) },
                    onRejectClick = { remarks -> rejectRequest(remarks) },
                    onUpgradeClick = { upgradeRequester() },
                )
            }
        }

        verifyAdminAndLoad()
    }

    override fun onResume() {
        super.onResume()
        if (requestId.isNotBlank()) {
            verifyAdminAndLoad()
        }
    }

    private fun verifyAdminAndLoad() {
        _isLoading.value = true
        _errorMessage.value = null

        lifecycleScope.launch {
            when (val result = repository.getCurrentUser()) {
                is NetworkResult.Success -> {
                    if (result.data.role != AccountRole.ADMIN && result.data.role != AccountRole.SUPER_ADMIN) {
                        _isLoading.value = false
                        _errorMessage.value = "Admin access required."
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
        lifecycleScope.launch {
            when (val result = repository.approveEvent(requestId, remarks)) {
                is NetworkResult.Success -> {
                    Toast.makeText(this@AdminEventRequestDetailActivity, "Event request approved!", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    loadRequest()
                }
                is NetworkResult.Error -> {
                    _isLoading.value = false
                    Toast.makeText(this@AdminEventRequestDetailActivity, toFriendlyError(result.message), Toast.LENGTH_LONG).show()
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun rejectRequest(remarks: String?) {
        _isLoading.value = true
        lifecycleScope.launch {
            when (val result = repository.rejectEvent(requestId, remarks)) {
                is NetworkResult.Success -> {
                    Toast.makeText(this@AdminEventRequestDetailActivity, "Request rejected.", Toast.LENGTH_SHORT).show()
                    loadRequest()
                }
                is NetworkResult.Error -> {
                    _isLoading.value = false
                    Toast.makeText(this@AdminEventRequestDetailActivity, toFriendlyError(result.message), Toast.LENGTH_LONG).show()
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun upgradeRequester() {
        _isLoading.value = true
        lifecycleScope.launch {
            when (val result = repository.upgradeOrganizer(requestId)) {
                is NetworkResult.Success -> {
                    Toast.makeText(this@AdminEventRequestDetailActivity, "Requester upgraded to Organizer.", Toast.LENGTH_SHORT).show()
                    loadRequest()
                }
                is NetworkResult.Error -> {
                    _isLoading.value = false
                    Toast.makeText(this@AdminEventRequestDetailActivity, toFriendlyError(result.message), Toast.LENGTH_LONG).show()
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun toFriendlyError(message: String): String {
        val normalized = message.lowercase()
        return when {
            normalized.contains("disabled") || normalized.contains("suspend") -> "Account is disabled. Contact support."
            normalized.contains("401") || normalized.contains("unauthorized") -> "Session expired. Please sign in again."
            normalized.contains("403") || normalized.contains("forbidden") || normalized.contains("admin access") -> "Admin access required."
            normalized.contains("404") || normalized.contains("not found") -> "Request not found."
            normalized.contains("400") || normalized.contains("invalid") || normalized.contains("bad request") -> "Action cannot be completed in the current state."
            normalized.contains("500") -> "Server error. Please try again later."
            normalized.contains("unable to resolve host") || normalized.contains("failed to connect") || normalized.contains("timeout") -> "No internet connection. Check your network and try again."
            else -> message
        }
    }

    companion object {
        const val EXTRA_REQUEST_ID = "extra_request_id"
    }
}
