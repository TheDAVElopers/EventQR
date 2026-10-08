package com.thedavelopers.eventqr.features.organizer.notifications

import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.util.UiStrings
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

/** The one thing the notifications screen shows at a time. */
enum class NotificationsScreenState { LOADING, ERROR, EMPTY, CONTENT }

/**
 * Loading, error and empty used to be three independent observers, so the screen could show the skeleton and the
 * empty message together (and flashed "empty" before the first request even started). This picks exactly one.
 *
 * @param loaded true once a request has finished since the list was last cleared; an empty list before that is
 * "nothing yet", not "no notifications".
 */
fun resolveNotificationsState(loading: Boolean, loaded: Boolean, hasError: Boolean, itemCount: Int): NotificationsScreenState =
    when {
        itemCount > 0 -> NotificationsScreenState.CONTENT
        loading || !loaded && !hasError -> NotificationsScreenState.LOADING
        hasError -> NotificationsScreenState.ERROR
        else -> NotificationsScreenState.EMPTY
    }

class OrganizerNotificationsViewModel(private val repo: OrganizerRepository, private val strings: UiStrings) : ViewModel() {

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

    private val _loaded = MutableLiveData(false)
    val loaded: LiveData<Boolean> = _loaded

    /** Only the newest request may change the screen, so a slow earlier response cannot overwrite a newer filter. */
    private var requestCounter = 0

    fun setEventFilter(eventId: UUID?) {
        if (_selectedEventId.value == eventId) return
        _selectedEventId.value = eventId
        load(reset = true)
    }

    fun setTypeFilter(type: NotificationType?) {
        if (_selectedType.value == type) return
        _selectedType.value = type
        load(reset = true)
    }

    /**
     * @param reset clear the shown list first (a filter changed, so the old rows no longer apply) and show the
     * skeleton; a plain reload (pull to refresh, mark all read) keeps the rows on screen instead.
     */
    fun load(reset: Boolean = false) {
        val request = ++requestCounter
        if (reset) {
            _list.value = emptyList()
            _loaded.value = false
        }
        _loading.value = true
        _error.value = null
        viewModelScope.launch {
            val result = repo.getMyNotifications(
                eventId = _selectedEventId.value,
                notificationType = _selectedType.value
            )
            if (request != requestCounter) return@launch
            _loading.value = false
            when (result) {
                is NetworkResult.Success -> {
                    _list.value = result.data
                    _loaded.value = true
                }
                is NetworkResult.Error -> _error.value = result.message
                else -> _error.value = strings.get(R.string.notifications_unknown_error)
            }
        }
    }

    fun markRead(item: NotificationResponse, onRequest: suspend (NotificationResponse) -> Unit) {
        viewModelScope.launch {
            onRequest(item)
            val updated = (_list.value ?: emptyList()).map {
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
