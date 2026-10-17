package com.thedavelopers.eventqr.features.staff

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.api.dto.PageResponse
import com.thedavelopers.eventqr.core.api.dto.RegistrationStatus
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.idprinting.AndroidIdPrinter
import com.thedavelopers.eventqr.features.idprinting.PrintPermissionLoad
import com.thedavelopers.eventqr.features.idprinting.model.dto.IdPrintResponse
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse
import com.thedavelopers.eventqr.features.staff.details.StaffAttendeeDetailsActivity
import com.thedavelopers.eventqr.features.staff.result.StaffScanResultActivity
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse
import kotlinx.coroutines.CompletableDeferred
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import retrofit2.HttpException
import retrofit2.Response
import java.util.UUID

/**
 * The staff Print IDs permission on the print screens: the affordances follow `canPrintId` read off the
 * assigned-event data, and a server 403 (the permission can be revoked mid-session) surfaces its message
 * without ever opening the system print dialog. On the batch screen an aborted batch also cancels the print
 * logs it already created, so the audit trail only ever holds IDs the user really printed.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class StaffPrintPermissionUiTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val eventId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val attendeeId = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val qrCredentialId = UUID.fromString("44444444-4444-4444-4444-444444444444")
    private val logId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val denialMessage = "You don't have permission to print IDs for this event"

    /** Records the print hand-off instead of opening the system dialog. */
    class RecordingDetailsActivity : StaffAttendeeDetailsActivity() {
        val openedCards = mutableListOf<AndroidIdPrinter.CardData>()

        override fun newRepository(): StaffRepository = injectedRepository ?: super.newRepository()

        override fun openSystemPrintDialog(cardData: AndroidIdPrinter.CardData, printLogId: UUID, serverMessage: String) {
            openedCards += cardData
        }

        companion object {
            var injectedRepository: StaffRepository? = null
        }
    }

    /** Records the batch hand-off instead of opening the system dialog, and snapshots when a message reached the user. */
    class RecordingRegistrationsActivity : EventRegistrationsActivity() {
        val openedBatches = mutableListOf<List<AndroidIdPrinter.CardData>>()

        /** The last message shown to the user, recorded before the toast goes out. */
        var lastMessage: String? = null

        /** Whether the batch's print-log cancel had already run when that message was shown. */
        var cancelCompletedWhenMessaged: Boolean? = null

        override fun newRepository(): StaffRepository = injectedRepository ?: super.newRepository()

        override fun openSystemBatchPrintDialog(
            cards: List<AndroidIdPrinter.CardData>,
            printLogIds: List<UUID>,
            selected: List<RegistrationResponse>,
            failures: List<Pair<String, String>>,
        ) {
            openedBatches += cards
        }

        override fun showMessage(message: String) {
            lastMessage = message
            cancelCompletedWhenMessaged = (injectedRepository as? FakeRepository)?.cancelCompleted
            super.showMessage(message)
        }

        companion object {
            var injectedRepository: StaffRepository? = null
        }
    }

    /** Records the ID_PRINT hand-off from the scan-result screen instead of opening the system dialog. */
    class RecordingScanResultActivity : StaffScanResultActivity() {
        val openedCards = mutableListOf<AndroidIdPrinter.CardData>()

        override fun newRepository(): StaffRepository = injectedRepository ?: super.newRepository()

        override fun openSystemPrintDialog(
            eventId: String,
            cardData: AndroidIdPrinter.CardData,
            printLogId: UUID,
            serverMessage: String,
        ) {
            openedCards += cardData
        }

        companion object {
            var injectedRepository: StaffRepository? = null
        }
    }

    /** Every network touchpoint the two screens use, so no test reaches the live backend. */
    private inner class FakeRepository(
        private val canPrintId: Boolean? = null,
        /** When true the assigned-event read fails, so the permission is unknown. */
        private val permissionLoadFails: Boolean = false,
        private val printResult: NetworkResult<IdPrintResponse>? = null,
        private val batchResult: NetworkResult<List<IdPrintResponse>> = NetworkResult.Loading,
        /** Per-call batch answers; falls back to [batchResult] once exhausted. */
        private val batchResults: List<NetworkResult<List<IdPrintResponse>>> = emptyList(),
        /** A test parks the cancel here to observe that the screen waited for it; completed by default. */
        private val cancelGate: CompletableDeferred<Unit> = CompletableDeferred(Unit),
        /** A test parks the permission read here to tap print while it is still loading. */
        private val permissionGate: CompletableDeferred<Unit> = CompletableDeferred(Unit),
    ) : StaffRepository(context) {
        var printCalls = 0
        var batchCalls = 0

        /** Every print-log confirmation the screens asked for, as (outcome, log ids). */
        val confirmCalls = mutableListOf<Pair<AndroidIdPrinter.PrintOutcome, List<UUID>>>()

        /** True once a confirmation finished, as opposed to merely having been scheduled. */
        var cancelCompleted = false

        override suspend fun printPermission(eventId: String): PrintPermissionLoad {
            permissionGate.await()
            return if (permissionLoadFails) PrintPermissionLoad.Failed else PrintPermissionLoad.Known(canPrintId)
        }

        override suspend fun getEvents() = NetworkResult.Success(emptyList<com.thedavelopers.eventqr.features.staff.model.dto.StaffAssignedEventResponse>())

        override suspend fun getAttendeeByEvent(eventId: String, attendeeId: String) = NetworkResult.Success(registration())

        override suspend fun getTransactionsByEvent(
            eventId: String,
            attendeeUserId: String?,
            page: Int,
            size: Int,
            result: String?,
        ): NetworkResult<PageResponse<TransactionResponse>> = NetworkResult.Success(PageResponse(totalElements = 0))

        override suspend fun getAttendeeTransactions(eventId: String, attendeeId: String) =
            NetworkResult.Success(emptyList<TransactionResponse>())

        override suspend fun getStaffPrintLogs(eventId: String) = NetworkResult.Success(emptyList<IdPrintResponse>())

        override suspend fun getIdCardVisibleFields(eventId: String): List<String> = emptyList()

        override suspend fun printAttendeeId(eventId: String, attendeeId: String): NetworkResult<IdPrintResponse> {
            printCalls++
            return printResult ?: NetworkResult.Loading
        }

        override suspend fun reprintAttendeeId(eventId: String, attendeeId: String): NetworkResult<IdPrintResponse> {
            printCalls++
            return printResult ?: NetworkResult.Loading
        }

        override suspend fun printIdBatch(
            eventId: String,
            attendeeUserIds: List<UUID>,
            reprint: Boolean,
        ): NetworkResult<List<IdPrintResponse>> {
            val call = batchCalls
            batchCalls++
            return batchResults.getOrNull(call) ?: batchResult
        }

        override suspend fun confirmPrintLogs(
            eventId: String,
            logIds: List<UUID>,
            outcome: AndroidIdPrinter.PrintOutcome,
        ): Int {
            confirmCalls += outcome to logIds
            cancelGate.await()
            cancelCompleted = true
            return logIds.distinct().size
        }

        override suspend fun getRegistrationsPage(
            eventId: String,
            query: String?,
            page: Int,
            size: Int,
        ): NetworkResult<PageResponse<RegistrationResponse>> = NetworkResult.Success(PageResponse())

        override suspend fun countRegistrations(eventId: String, status: RegistrationStatus?) = NetworkResult.Success(0L)

        override suspend fun getAllRegistrations(
            eventId: String,
            query: String?,
        ): NetworkResult<List<RegistrationResponse>> = NetworkResult.Success(emptyList())
    }

    private fun registration() = RegistrationResponse(
        registrationId = UUID.randomUUID(),
        eventId = eventId,
        attendeeUserId = attendeeId,
        attendeeEmail = "jane@test.com",
        attendeeName = "Jane Dela Cruz",
        status = RegistrationStatus.REGISTERED,
        qrCredentialId = qrCredentialId,
    )

    private fun httpError(code: Int, message: String): NetworkResult.Error {
        val body = """{"success":false,"message":"$message"}""".toResponseBody("application/json".toMediaType())
        return NetworkResult.Error(message, HttpException(Response.error<Any>(code, body)), message)
    }

    @Before
    fun setUp() {
        SessionManager(context).apply { clearSession(); saveRole(AccountRole.STAFF) }
        RecordingDetailsActivity.injectedRepository = null
        RecordingRegistrationsActivity.injectedRepository = null
        RecordingScanResultActivity.injectedRepository = null
    }

    /** Coroutines post back to the main looper (this thread); pump it until [condition] holds. */
    private fun awaitMain(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
            shadowOf(Looper.getMainLooper()).idle()
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun detailsActivity(fake: StaffRepository): RecordingDetailsActivity {
        RecordingDetailsActivity.injectedRepository = fake
        val intent = Intent().apply {
            putExtra(StaffScreenExtras.EXTRA_EVENT_ID, eventId.toString())
            putExtra(StaffScreenExtras.EXTRA_ATTENDEE_ID, attendeeId.toString())
            putExtra(StaffScreenExtras.EXTRA_QR_CREDENTIAL_ID, qrCredentialId.toString())
        }
        return Robolectric.buildActivity(RecordingDetailsActivity::class.java, intent).create().get()
    }

    private fun registrationsActivity(fake: StaffRepository): RecordingRegistrationsActivity {
        RecordingRegistrationsActivity.injectedRepository = fake
        val intent = Intent().apply { putExtra(StaffScreenExtras.EXTRA_EVENT_ID, eventId.toString()) }
        return Robolectric.buildActivity(RecordingRegistrationsActivity::class.java, intent).create().get()
    }

    private fun scanResultActivity(fake: StaffRepository): RecordingScanResultActivity {
        RecordingScanResultActivity.injectedRepository = fake
        val intent = Intent().apply {
            putExtra(StaffScreenExtras.EXTRA_EVENT_ID, eventId.toString())
            putExtra(StaffScreenExtras.EXTRA_ATTENDEE_ID, attendeeId.toString())
            putExtra(StaffScreenExtras.EXTRA_ATTENDEE_NAME, "Jane Dela Cruz")
            putExtra(StaffScreenExtras.EXTRA_EVENT_TITLE, "Test Event")
            putExtra(StaffScreenExtras.EXTRA_IS_VALID, true)
            putExtra(StaffScreenExtras.EXTRA_QR_ACTIVE, true)
            putExtra(StaffScreenExtras.EXTRA_SCAN_PURPOSE_CODE, "ID_PRINT")
        }
        return Robolectric.buildActivity(RecordingScanResultActivity::class.java, intent).create().get()
    }

    private fun printButton(activity: StaffAttendeeDetailsActivity) = activity.findViewById<View>(R.id.btnPrintOrReprintId)

    private fun permissionNotice(activity: StaffAttendeeDetailsActivity) = activity.findViewById<TextView>(R.id.txtPrintPermissionNotice)

    // ---- the print affordance follows canPrintId

    @Test
    fun canPrintIdFalse_disablesThePrintControlAndExplainsHowToGetIt() {
        val activity = detailsActivity(FakeRepository(canPrintId = false))
        awaitMain { permissionNotice(activity).visibility == View.VISIBLE }

        assertEquals(View.VISIBLE, printButton(activity).visibility)
        assertFalse(printButton(activity).isEnabled)
        assertEquals(0.5f, printButton(activity).alpha, 0.001f)
        assertEquals(View.VISIBLE, permissionNotice(activity).visibility)
        assertEquals(
            context.getString(R.string.staff_print_id_permission_required),
            permissionNotice(activity).text.toString(),
        )
    }

    @Test
    fun canPrintIdTrue_keepsThePrintControlEnabled() {
        val activity = detailsActivity(FakeRepository(canPrintId = true))
        awaitMain { printButton(activity).isEnabled }

        assertTrue(printButton(activity).isEnabled)
        assertEquals(1f, printButton(activity).alpha, 0.001f)
        assertEquals(View.GONE, permissionNotice(activity).visibility)
    }

    @Test
    fun canPrintIdFalse_blocksThePrintAttemptBeforeTheServer() {
        val fake = FakeRepository(canPrintId = false, printResult = NetworkResult.Success(printResponse()))
        val activity = detailsActivity(fake)
        awaitMain { permissionNotice(activity).visibility == View.VISIBLE }

        printButton(activity).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(0, fake.printCalls)
        assertTrue(activity.openedCards.isEmpty())
        assertEquals(context.getString(R.string.staff_print_id_permission_required), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun canPrintIdTrue_letsAPrintThroughToTheDialog() {
        val activity = detailsActivity(FakeRepository(canPrintId = true))
        awaitMain { printButton(activity).isEnabled }

        activity.onPrintResult(NetworkResult.Success(printResponse()), emptyList(), "")

        assertEquals(1, activity.openedCards.size)
    }

    // ---- a mid-session 403

    @Test
    fun printRefusedWith403_showsTheServerMessageAndNeverOpensThePrintDialog() {
        val activity = detailsActivity(FakeRepository(canPrintId = true))
        awaitMain { printButton(activity).isEnabled }

        activity.onPrintResult(httpError(403, denialMessage), emptyList(), "")

        assertTrue(activity.openedCards.isEmpty())
        assertEquals(denialMessage, ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun printRefusedWith403WithoutABodyMessage_fallsBackToTheLocalCopy() {
        val activity = detailsActivity(FakeRepository(canPrintId = true))
        awaitMain { printButton(activity).isEnabled }

        activity.onPrintResult(NetworkResult.Error("Request failed", HttpException(Response.error<Any>(403, "".toResponseBody(null)))), emptyList(), "")

        assertTrue(activity.openedCards.isEmpty())
        assertEquals(context.getString(R.string.staff_print_permission_denied), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun aNonPermissionPrintError_showsItsOwnMessage() {
        val activity = detailsActivity(FakeRepository(canPrintId = true))
        awaitMain { printButton(activity).isEnabled }

        activity.onPrintResult(httpError(500, "Active ID template not found for event"), emptyList(), "")

        assertTrue(activity.openedCards.isEmpty())
        assertEquals("Active ID template not found for event", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun printControl_isDisabledWithoutNoticeUntilThePermissionIsKnown() {
        val activity = detailsActivity(FakeRepository(canPrintId = true, permissionLoadFails = true))
        awaitMain { printButton(activity).visibility == View.VISIBLE }

        assertFalse(printButton(activity).isEnabled)
        assertEquals(View.GONE, permissionNotice(activity).visibility)
    }

    @Test
    fun printTapWhilePermissionLoads_saysCheckingNotFailed() {
        val gate = CompletableDeferred<Unit>()
        val fake = FakeRepository(canPrintId = true, printResult = NetworkResult.Success(printResponse()), permissionGate = gate)
        val activity = detailsActivity(fake)
        shadowOf(Looper.getMainLooper()).idle()

        activity.printId()

        assertEquals(context.getString(R.string.staff_print_permission_checking), ShadowToast.getTextOfLatestToast())
        assertEquals(0, fake.printCalls)
    }

    @Test
    fun printTapAfterPermissionLoadFailed_saysCheckFailed() {
        val fake = FakeRepository(permissionLoadFails = true, printResult = NetworkResult.Success(printResponse()))
        val activity = detailsActivity(fake)
        awaitMain { printButton(activity).visibility == View.VISIBLE }

        activity.printId()

        assertEquals(context.getString(R.string.staff_print_permission_check_failed), ShadowToast.getTextOfLatestToast())
        assertEquals(0, fake.printCalls)
    }

    @Test
    fun permissionLoadFailure_keepsPrintDisabledAndBlocksThePrintAttempt() {
        val fake = FakeRepository(permissionLoadFails = true, printResult = NetworkResult.Success(printResponse()))
        val activity = detailsActivity(fake)
        awaitMain { printButton(activity).visibility == View.VISIBLE }

        assertFalse(printButton(activity).isEnabled)
        printButton(activity).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(0, fake.printCalls)
    }

    @Test
    fun permissionLoadFailure_hidesTheBatchPrintSelectIcon() {
        val activity = registrationsActivity(FakeRepository(canPrintId = true, permissionLoadFails = true))
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(100)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(View.GONE, activity.findViewById<View>(R.id.btnSelectForPrint).visibility)
    }

    // ---- the batch screen

    @Test
    fun canPrintIdFalse_hidesTheBatchPrintSelectIcon() {
        val activity = registrationsActivity(FakeRepository(canPrintId = false))
        awaitMain { activity.findViewById<View>(R.id.btnSelectForPrint).visibility == View.GONE }

        assertEquals(View.GONE, activity.findViewById<View>(R.id.btnSelectForPrint).visibility)
    }

    @Test
    fun canPrintIdTrue_showsTheBatchPrintSelectIcon() {
        val activity = registrationsActivity(FakeRepository(canPrintId = true))
        awaitMain { activity.findViewById<View>(R.id.btnSelectForPrint).visibility == View.VISIBLE }

        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.btnSelectForPrint).visibility)
    }

    @Test
    fun batchRefusedWith403_stopsAtTheRefusalAndShowsTheServerMessageWithoutPrinting() {
        val fake = FakeRepository(canPrintId = true, batchResult = httpError(403, denialMessage))
        val activity = registrationsActivity(fake)
        val selected = (1..3).map { registration() }

        activity.executeBatchPrint(selected.map { card() }, selected)
        awaitMain { fake.batchCalls > 0 }

        assertEquals(1, fake.batchCalls)
        assertTrue(activity.openedBatches.isEmpty())
        assertEquals(denialMessage, ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun batchRefusedMidwayWith403_cancelsTheLogsCreatedBeforeTheRefusalAndPrintsNothing() {
        val firstLogId = UUID.randomUUID()
        val cancelGate = CompletableDeferred<Unit>()
        val fake = FakeRepository(
            canPrintId = true,
            batchResults = listOf(
                NetworkResult.Success(listOf(printResponse(firstLogId))),
                httpError(403, denialMessage),
            ),
            cancelGate = cancelGate,
        )
        val activity = registrationsActivity(fake)
        val selected = (1..3).map { registration() }

        activity.executeBatchPrint(selected.map { card() }, selected)
        awaitMain { fake.confirmCalls.isNotEmpty() }

        // The batch is parked inside the cancel, so nothing has been shown to the user yet: the cancel is
        // awaited, not merely scheduled onto a background scope.
        assertEquals(2, fake.batchCalls)
        assertTrue(activity.openedBatches.isEmpty())
        assertFalse(fake.cancelCompleted)
        assertNull(activity.lastMessage)

        cancelGate.complete(Unit)
        awaitMain { activity.lastMessage != null }

        assertTrue(fake.cancelCompleted)
        assertTrue(
            "the denial must be shown only once the cancel has completed",
            activity.cancelCompletedWhenMessaged == true,
        )
        assertEquals(denialMessage, activity.lastMessage)
        assertEquals(denialMessage, ShadowToast.getTextOfLatestToast())
        val confirmed = fake.confirmCalls.single()
        assertEquals(AndroidIdPrinter.PrintOutcome.CANCELLED, confirmed.first)
        assertEquals(listOf(firstLogId), confirmed.second)
    }

    @Test
    fun batchAllowed_printsEverySelectedAttendeeInOneDialog() {
        val fake = FakeRepository(canPrintId = true, batchResult = NetworkResult.Success(listOf(printResponse())))
        val activity = registrationsActivity(fake)
        val selected = (1..3).map { registration() }

        activity.executeBatchPrint(selected.map { card() }, selected)
        awaitMain { activity.openedBatches.isNotEmpty() }

        assertEquals(3, fake.batchCalls)
        assertEquals(1, activity.openedBatches.size)
        assertEquals(3, activity.openedBatches.single().size)
    }

    // ---- the scan-result screen (reachable without the scanner's filtered purpose picker)

    @Test
    fun idPrintRefusedWith403OnTheScanResultScreen_showsThePermissionCopyWithoutPrinting() {
        val fake = FakeRepository(printResult = httpError(403, denialMessage))
        val activity = scanResultActivity(fake)

        activity.findViewById<View>(R.id.btnContinueTransaction).performClick()
        awaitMain { fake.printCalls > 0 }

        assertEquals(1, fake.printCalls)
        assertTrue(activity.openedCards.isEmpty())
        assertEquals(
            context.getString(R.string.staff_print_id_permission_required),
            ShadowToast.getTextOfLatestToast(),
        )
        // Not the generic ID-print-failed panel: the print was refused, the scan itself still stands.
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.headerApproved).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.headerRejected).visibility)
    }

    @Test
    fun idPrintAllowedOnTheScanResultScreen_opensThePrintDialog() {
        val fake = FakeRepository(printResult = NetworkResult.Success(printResponse()))
        val activity = scanResultActivity(fake)

        activity.findViewById<View>(R.id.btnContinueTransaction).performClick()
        awaitMain { activity.openedCards.isNotEmpty() }

        assertEquals(1, activity.openedCards.size)
    }

    private fun card() = AndroidIdPrinter.CardData(attendeeName = "Jane Dela Cruz")

    private fun printResponse(printLogId: UUID = logId) = IdPrintResponse(
        printLogId = printLogId,
        eventId = eventId,
        attendeeUserId = attendeeId,
        registrationId = UUID.randomUUID(),
        qrCredentialId = qrCredentialId,
        reprint = false,
        success = true,
        message = "Print logged",
    )
}
