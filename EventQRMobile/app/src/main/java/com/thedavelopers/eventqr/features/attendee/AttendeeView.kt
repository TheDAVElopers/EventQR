package com.thedavelopers.eventqr.features.attendee

interface AttendeeView {
    fun showLoading(isLoading: Boolean)
    fun showMessage(message: String)
}
