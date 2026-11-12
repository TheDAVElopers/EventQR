package com.thedavelopers.eventqr.features.auth

import com.thedavelopers.eventqr.core.api.NetworkResult
import retrofit2.HttpException

/**
 * What an auth presenter may write to the log for a failed call: the HTTP status and the exception
 * class, nothing else. The server's own message can carry an email address or an account detail
 * (a 409 names the email), and `Log.w` ships in release builds.
 */
internal fun NetworkResult.Error.logSummary(): String =
    "status=${(throwable as? HttpException)?.code()} cause=${throwable?.javaClass?.simpleName}"
