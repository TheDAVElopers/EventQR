package com.thedavelopers.eventqr.core.api

sealed class NetworkResult<out T> {
    data class Success<T>(val data: T, val message: String? = null) : NetworkResult<T>()
    /**
     * [serverMessage] is the message parsed from the backend error body (null when absent/blank or not an
     * HTTP error). The body is single-read, so it is captured once in [safeApiCall] rather than re-parsed.
     */
    data class Error(
        val message: String,
        val throwable: Throwable? = null,
        val serverMessage: String? = null,
        /** Seconds from a `Retry-After` response header (429s); null when absent or unparseable. */
        val retryAfterSeconds: Long? = null,
    ) : NetworkResult<Nothing>()
    data object Loading : NetworkResult<Nothing>()
}
