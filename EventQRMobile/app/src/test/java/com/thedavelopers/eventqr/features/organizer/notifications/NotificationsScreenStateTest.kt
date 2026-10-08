package com.thedavelopers.eventqr.features.organizer.notifications

import com.thedavelopers.eventqr.features.organizer.notifications.NotificationsScreenState.CONTENT
import com.thedavelopers.eventqr.features.organizer.notifications.NotificationsScreenState.EMPTY
import com.thedavelopers.eventqr.features.organizer.notifications.NotificationsScreenState.ERROR
import com.thedavelopers.eventqr.features.organizer.notifications.NotificationsScreenState.LOADING
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationsScreenStateTest {

    @Test
    fun beforeAnythingLoadsTheScreenIsLoadingNeverEmpty() {
        // The list starts as an empty list: that must not read as "no notifications".
        assertEquals(LOADING, resolveNotificationsState(loading = false, loaded = false, hasError = false, itemCount = 0))
        assertEquals(LOADING, resolveNotificationsState(loading = true, loaded = false, hasError = false, itemCount = 0))
    }

    @Test
    fun emptyOnlyAfterALoadFinishedWithNoRows() {
        assertEquals(EMPTY, resolveNotificationsState(loading = false, loaded = true, hasError = false, itemCount = 0))
    }

    @Test
    fun reloadingAnEmptyListShowsTheSkeletonNotTheEmptyMessage() {
        assertEquals(LOADING, resolveNotificationsState(loading = true, loaded = true, hasError = false, itemCount = 0))
    }

    @Test
    fun rowsAlwaysWinSoARefreshNeverCoversThemWithTheSkeleton() {
        assertEquals(CONTENT, resolveNotificationsState(loading = true, loaded = true, hasError = false, itemCount = 3))
        assertEquals(CONTENT, resolveNotificationsState(loading = false, loaded = true, hasError = true, itemCount = 3))
    }

    @Test
    fun errorWithNothingToShowIsAnErrorNotAnEmptyList() {
        assertEquals(ERROR, resolveNotificationsState(loading = false, loaded = false, hasError = true, itemCount = 0))
        assertEquals(ERROR, resolveNotificationsState(loading = false, loaded = true, hasError = true, itemCount = 0))
    }

    @Test
    fun retryingAfterAnErrorShowsTheSkeleton() {
        assertEquals(LOADING, resolveNotificationsState(loading = true, loaded = false, hasError = false, itemCount = 0))
    }
}
