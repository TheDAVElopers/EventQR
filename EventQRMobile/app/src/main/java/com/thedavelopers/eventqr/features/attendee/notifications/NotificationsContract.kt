package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.features.notifications.model.dto.NotificationResponse

interface NotificationsContract {
    interface View : AttendeeView {
        fun renderNotifications(items: List<NotificationResponse>)
    }
}
