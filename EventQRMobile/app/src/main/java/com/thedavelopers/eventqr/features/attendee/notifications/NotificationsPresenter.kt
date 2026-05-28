package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.core.api.NetworkResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class NotificationsPresenter(
    private var view: NotificationsContract.View?,
    private val repository: AttendeeRepository,
) {
    private var job: Job? = null

    fun detach() {
        job?.cancel()
        view = null
    }

    fun load(recipientUserId: String) {
        view?.showLoading(true)
        job = kotlinx.coroutines.MainScope().launch {
            when (val result = repository.getNotificationsByRecipient(recipientUserId)) {
                is NetworkResult.Success -> {
                    view?.showLoading(false)
                    view?.renderNotifications(result.data)
                }
                is NetworkResult.Error -> {
                    view?.showLoading(false)
                    view?.showMessage(result.message)
                }
                NetworkResult.Loading -> Unit
            }
        }
    }
}
