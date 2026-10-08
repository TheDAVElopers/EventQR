package com.thedavelopers.eventqr.core.api

import com.thedavelopers.eventqr.core.api.dto.ApiResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException

private val errorParser = sharedGson()

@Suppress("UNCHECKED_CAST")
suspend fun <T> safeApiCall(call: suspend () -> ApiResponse<T>): NetworkResult<T> {
    return withContext(Dispatchers.IO) {
        runCatching { call() }
            .fold(
                onSuccess = { response ->
                    if (response.success) {
                        response.data?.let { data ->
                            NetworkResult.Success(data, response.message)
                        } ?: NetworkResult.Success(Unit as T, response.message)
                    } else {
                        NetworkResult.Error(response.message ?: "Request failed")
                    }
                },
                onFailure = { throwable ->
                    val serverMessage = (throwable as? HttpException)?.let { parseHttpErrorMessage(it) }
                    val retryAfter = (throwable as? HttpException)?.let { parseRetryAfterSeconds(it.response()?.headers()?.get("Retry-After")) }
                    NetworkResult.Error(extractMessage(throwable, serverMessage), throwable, serverMessage, retryAfter)
                }
            )
    }
}

/** Parses a `Retry-After` delta-seconds value; null for null/blank/non-numeric (e.g. HTTP-date) or non-positive input. */
internal fun parseRetryAfterSeconds(value: String?): Long? =
    value?.trim()?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toLongOrNull()?.takeIf { it > 0 }

/** Reads the backend ErrorResponse `message` from an HTTP failure's body; null when absent or blank. */
internal fun parseHttpErrorMessage(throwable: HttpException): String? {
    val body = runCatching { throwable.response()?.errorBody()?.string().orEmpty() }.getOrDefault("")
    return parseErrorMessage(body)?.takeIf { it.isNotBlank() }
}

private fun extractMessage(throwable: Throwable, serverMessage: String?): String {
    if (throwable is HttpException) {
        if (!serverMessage.isNullOrBlank()) {
            return serverMessage
        }
        return throwable.message().ifBlank { "Request failed" }
    }
    return when (throwable) {
        is java.net.UnknownHostException, is java.net.ConnectException ->
            "Can't reach the server. Check your internet connection and try again."
        is java.net.SocketTimeoutException ->
            "The server took too long to respond. Please try again."
        is java.io.IOException ->
            "Network problem. Check your connection and try again."
        else -> throwable.message?.takeIf { it.isNotBlank() } ?: "Request failed"
    }
}

private fun parseErrorMessage(errorBody: String): String? {
    if (errorBody.isBlank()) {
        return null
    }
    return runCatching {
        val response = errorParser.fromJson(errorBody, ApiResponse::class.java)
        response?.message?.toString()
    }.getOrNull().takeIf { !it.isNullOrBlank() }
        ?: runCatching { extractMessageFromJson(errorBody) }.getOrNull()
}

private fun extractMessageFromJson(errorBody: String): String? {
    val messageRegex = "\"message\"\\s*:\\s*\"([^\"]+)\"".toRegex()
    return messageRegex.find(errorBody)?.groupValues?.getOrNull(1)
}