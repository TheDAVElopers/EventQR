package com.thedavelopers.eventqr.features.organizer.notifications

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.NotificationStatus
import com.thedavelopers.eventqr.core.api.dto.NotificationType
import com.thedavelopers.eventqr.features.notifications.model.dto.NotificationResponse
import com.thedavelopers.eventqr.features.organizer.OrganizerRepository
import kotlinx.coroutines.launch
import java.util.UUID

class OrganizerNotificationsViewModel(private val repo: OrganizerRepository) : ViewModel() {

    private val _selectedEventId = MutableLiveData<UUID?>(null)
    private val _selectedType = MutableLiveData<NotificationType?>(null)

    val selectedEventId: LiveData<UUID?> = _selectedEventId
    val selectedType: LiveData<NotificationType?> = _selectedType

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    private val _list = MutableLiveData<List<NotificationResponse>>(emptyList())
    val list: LiveData<List<NotificationResponse>> = _list

    fun setEventFilter(eventId: UUID?) {
        _selectedEventId.value = eventId
        load()
    }

    fun setTypeFilter(type: NotificationType?) {
        _selectedType.value = type
        load()
    }

    fun load() {
        _loading.value = true
        _error.value = null
        viewModelScope.launch {
            val result = repo.getMyNotifications(
                eventId = _selectedEventId.value,
                notificationType = _selectedType.value
            )
            _loading.value = false
            when (result) {
                is NetworkResult.Success -> _list.value = result.data
                is NetworkResult.Error -> _error.value = result.message
                else -> _error.value = "Unknown error"
            }
        }
    }

    fun markRead(item: NotificationResponse, onRequest: suspend (NotificationResponse) -> Unit) {
        viewModelScope.launch {
            onRequest(item)
            val updated = _list.value?.map {
                if (it.notificationId == item.notificationId) it.copy(status = NotificationStatus.READ, readAt = null) else it
            }
            _list.value = updated
        }
    }

    fun markAllRead(onRequest: suspend () -> Unit) {
        viewModelScope.launch {
            onRequest()
            load()
        }
    }

    fun refresh() = load()
}