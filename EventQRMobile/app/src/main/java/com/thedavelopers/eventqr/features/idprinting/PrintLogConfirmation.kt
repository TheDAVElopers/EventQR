package com.thedavelopers.eventqr.features.idprinting

import android.util.Log
import com.thedavelopers.eventqr.core.api.ApiService
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.safeApiCall
import com.thedavelopers.eventqr.features.idprinting.AndroidIdPrinter.PrintOutcome
import com.thedavelopers.eventqr.features.idprinting.model.dto.IdPrintConfirmRequest
import com.thedavelopers.eventqr.features.idprinting.model.dto.IdPrintResponse
import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.IOException
import java.util.UUID

/**
 * Print logs are created PENDING when the app asks to print, then confirmed with what actually
 * happened in the system print dialog. Only PRINTED logs count as a printed ID.
 */
object PrintLogStatus {
    const val PENDING = "PENDING"
    const val PRINTED = "PRINTED"
    const val CANCELLED = "CANCELLED"
    const val FAILED = "FAILED"

    /** The confirm-endpoint outcome for what the print dialog reported. */
    fun confirmOutcomeFor(outcome: PrintOutcome): String = when (outcome) {
        PrintOutcome.SENT -> PRINTED
        PrintOutcome.CANCELLED -> CANCELLED
        PrintOutcome.FAILED -> FAILED
    }

    /** A log with no status comes from a server that predates confirmation, where every log was a print. */
    fun countsAsPrinted(status: String?): Boolean = status.isNullOrBlank() || status.equals(PRINTED, ignoreCase = true)
}

fun IdPrintResponse.countsAsPrinted(): Boolean = PrintLogStatus.countsAsPrinted(status)

private const val TAG = "PrintLogConfirmation"

/**
 * Waits before each retry of a confirmation that failed transiently: 3 attempts in all, backing off exponentially.
 * In-process only; the server stops accepting confirmations after its grace period anyway.
 */
internal val PRINT_CONFIRM_RETRY_DELAYS_MS: List<Long> = listOf(1_000L, 2_000L)

/**
 * Confirms each of [logIds] with [outcome], one request per log. Offline/timeouts and 5xx are retried after each
 * of [retryDelaysMs]; a 4xx is final. Best effort: a log that still fails is logged and skipped so the remaining
 * logs are still confirmed. Returns how many were confirmed.
 */
internal suspend fun confirmPrintLogs(
    api: ApiService,
    eventId: String,
    logIds: List<UUID>,
    outcome: PrintOutcome,
    retryDelaysMs: List<Long> = PRINT_CONFIRM_RETRY_DELAYS_MS,
): Int {
    val request = IdPrintConfirmRequest(PrintLogStatus.confirmOutcomeFor(outcome))
    var confirmed = 0
    for (logId in logIds.distinct()) {
        var attempt = 0
        while (true) {
            val result = safeApiCall { api.confirmPrintLog(eventId, logId.toString(), request) }
            if (result is NetworkResult.Success) {
                confirmed++
                break
            }
            val error = result as? NetworkResult.Error ?: break
            if (isTransient(error) && attempt < retryDelaysMs.size) {
                delay(retryDelaysMs[attempt])
                attempt++
                continue
            }
            val code = (error.throwable as? HttpException)?.code()
            // 409: the log is already final (e.g. a retried confirmation); nothing left to do.
            if (code == 409) {
                Log.i(TAG, "Print log $logId already confirmed; skipped ${request.outcome}")
            } else {
                Log.w(TAG, "Print log $logId not confirmed as ${request.outcome} (http=$code, attempts=${attempt + 1}): ${error.message}")
            }
            break
        }
    }
    return confirmed
}

/** Worth retrying: no response (offline, timeout) or a server error. Never a 4xx. */
private fun isTransient(error: NetworkResult.Error): Boolean = when (val t = error.throwable) {
    is IOException -> true
    is HttpException -> t.code() >= 500
    else -> false
}
