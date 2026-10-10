package com.thedavelopers.eventqr.features.staff

import android.content.Context
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.api.dto.PageResponse
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.staff.details.StaffAttendeeDetailsActivity
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class StaffAttendeeDetailsTransactionCountTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private class RecordingRepository(context: Context) : StaffRepository(context) {
        var lastResult: String? = "unset"
        var lastSize: Int? = null

        override suspend fun getTransactionsByEvent(
            eventId: String,
            attendeeUserId: String?,
            page: Int,
            size: Int,
            result: String?,
        ): NetworkResult<PageResponse<TransactionResponse>> {
            lastResult = result
            lastSize = size
            return NetworkResult.Success(PageResponse(totalElements = 7))
        }
    }

    @Test
    fun transactionCount_requestsApprovedOnly_andShowsTotalElements() {
        SessionManager(context).apply { clearSession(); saveRole(AccountRole.STAFF) }
        // No attendee extras: onCreate skips the network load, then the test drives loadTransactions directly.
        val activity = Robolectric.buildActivity(StaffAttendeeDetailsActivity::class.java).create().get()
        val fake = RecordingRepository(context)
        set(activity, "repository", fake)
        set(activity, "eventId", "event-1")
        set(activity, "attendeeId", "attendee-1")

        StaffAttendeeDetailsActivity::class.java.getDeclaredMethod("loadTransactions")
            .apply { isAccessible = true }.invoke(activity)

        assertEquals("APPROVED", fake.lastResult)
        assertEquals(1, fake.lastSize)
        assertEquals("7", activity.findViewById<TextView>(R.id.txtDetailTransactionCount).text.toString())
    }

    private fun set(target: Any, name: String, value: Any) {
        StaffAttendeeDetailsActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.set(target, value)
    }
}
