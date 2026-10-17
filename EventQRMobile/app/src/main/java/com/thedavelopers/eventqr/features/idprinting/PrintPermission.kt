package com.thedavelopers.eventqr.features.idprinting

import com.thedavelopers.eventqr.core.api.NetworkResult
import retrofit2.HttpException

/**
 * The staff "Print IDs" permission (`can_print_id`) behind every ID-print affordance.
 *
 * The flag travels on `StaffAssignedEventResponse.canPrintId`, but the organizer can change it
 * mid-session, so the backend enforces it on every print route as well: a refused print comes back
 * as HTTP 403 and is handled exactly like `canPrintId == false`.
 */
object PrintPermission {

    /**
     * Whether the print affordances may be offered for a `canPrintId` read off the assigned-event list.
     *
     * `null` means the caller carries no flag for that event — the organizer/ADMIN bypass, an
     * unassigned caller, or a list that couldn't be read — so the affordance stays available and the
     * server's own check decides.
     */
    fun isPrintAllowed(canPrintId: Boolean?): Boolean = canPrintId != false

    /** True when a print request was refused by the permission check (HTTP 403), not by another failure. */
    fun isPermissionDenied(error: NetworkResult.Error): Boolean =
        (error.throwable as? HttpException)?.code() == HTTP_FORBIDDEN

    /** The server's refusal message from [error], or [fallback] when the 403 body carried none. */
    fun deniedMessage(error: NetworkResult.Error, fallback: String): String =
        error.serverMessage?.takeIf { it.isNotBlank() } ?: fallback

    private const val HTTP_FORBIDDEN = 403
}
