package com.thedavelopers.eventqr.features.attendee

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.features.events.EventRequestDetailScreen
import com.thedavelopers.eventqr.features.events.model.dto.EventRequestResponse
import com.thedavelopers.eventqr.ui.theme.EventQrTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class AttendeeEventRequestDetailActivity : AppCompatActivity() {
    private lateinit var repository: AttendeeRepository
    private var requestId: String = ""

    private val _request = MutableStateFlow<EventRequestResponse?>(null)
    private val _isLoading = MutableStateFlow(false)
    private val _isRefreshing = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = AttendeeRepository(this)
        requestId = intent.getStringExtra(EXTRA_EVENT_REQUEST_ID).orEmpty()

        setContent {
            EventQrTheme {
                val request = _request.collectAsStateWithLifecycle().value
                val isLoading = _isLoading.collectAsStateWithLifecycle().value
                val isRefreshing = _isRefreshing.collectAsStateWithLifecycle().value
                val errorMessage = _errorMessage.collectAsStateWithLifecycle().value

                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { loadRequest() },
                    modifier = Modifier,
                ) {
                    EventRequestDetailScreen(
                        request = request,
                        isLoading = isLoading,
                        errorMessage = errorMessage,
                        isAdmin = false,
                        onBackClick = { finish() },
                        onRetryClick = { loadRequest() },
                    )
                }
            }
        }

        loadRequest()
    }

    private fun loadRequest() {
        if (requestId.isBlank()) {
            _errorMessage.value = "Missing event request information."
            return
        }

        _isRefreshing.value = _request.value != null
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
                    _errorMessage.value = result.message.ifBlank { "Unable to load request details." }
                }
                NetworkResult.Loading -> Unit
            }
            _isRefreshing.value = false
        }
    }

    companion object {
        const val EXTRA_EVENT_REQUEST_ID = "extra_event_request_id"
    }
}
