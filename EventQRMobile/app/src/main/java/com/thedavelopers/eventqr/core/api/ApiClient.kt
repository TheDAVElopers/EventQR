package com.thedavelopers.eventqr.core.api

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import com.thedavelopers.eventqr.core.session.SessionEvents
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.thedavelopers.eventqr.core.api.dto.ApiResponse
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.features.auth.model.dto.LoginResponse
import com.thedavelopers.eventqr.features.auth.model.dto.RefreshRequest
import retrofit2.http.Body
import retrofit2.http.POST
import java.io.IOException
import java.time.Instant

object ApiClient {
    @Volatile
    private var apiService: ApiService? = null

    fun getService(context: Context): ApiService {
        return apiService ?: synchronized(this) {
            apiService ?: buildService(context.applicationContext).also { apiService = it }
        }
    }

    /**
     * The one HTTP client every API call goes through: it attaches the access token and, on a
     * 401, silently trades the refresh token for a new one and retries.
     */
    fun newHttpClient(context: Context): OkHttpClient {
        val sessionManager = SessionManager(context.applicationContext)
        return OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(sessionManager))
            .authenticator(TokenAuthenticator(sessionManager, HttpRefreshCall(), SessionEvents::notifySessionExpired))
            .build()
    }

    private fun buildService(context: Context): ApiService {
        val client = newHttpClient(context)

        return Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(sharedGson()))
            .build()
            .create(ApiService::class.java)
    }
}

internal fun sharedGson() = GsonBuilder()
    .registerTypeAdapter(Instant::class.java, InstantTypeAdapter)
    .setLenient()
    .create()

private interface RefreshApi {
    @POST("auth/refresh")
    fun refresh(@Body request: RefreshRequest): Call<ApiResponse<LoginResponse>>
}

/** Talks to the refresh endpoint on a bare client, so it can never trigger itself. */
private class HttpRefreshCall : RefreshCall {
    private val api: RefreshApi by lazy {
        Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_URL)
            .client(OkHttpClient())
            .addConverterFactory(GsonConverterFactory.create(sharedGson()))
            .build()
            .create(RefreshApi::class.java)
    }

    override fun refresh(refreshToken: String): RefreshOutcome = try {
        val response = api.refresh(RefreshRequest(refreshToken)).execute()
        val data = response.body()?.takeIf { it.success }?.data
        when {
            response.isSuccessful && data != null -> RefreshOutcome.Success(data.accessToken, data.refreshToken)
            // Only a definite "no" ends the session. 429 (rate limited) and 5xx are transient.
            response.code() == 400 || response.code() == 401 || response.code() == 403 -> RefreshOutcome.Rejected
            else -> RefreshOutcome.Unavailable
        }
    } catch (_: IOException) {
        RefreshOutcome.Unavailable
    } catch (_: JsonParseException) {
        RefreshOutcome.Unavailable
    }
}
