package com.thedavelopers.eventqr.features.registrations

import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse
import java.time.Duration
import java.time.Instant

object RegistrationsCache {

    private val freshness = Duration.ofSeconds(30)

    @Volatile
    private var registrations: List<RegistrationResponse>? = null

    @Volatile
    private var fetchedAt: Instant? = null

    fun get(): List<RegistrationResponse>? = registrations

    fun isFresh(now: Instant = Instant.now()): Boolean {
        val fetched = fetchedAt ?: return false
        return !now.isAfter(fetched.plus(freshness))
    }

    @Synchronized
    fun set(items: List<RegistrationResponse>) {
        registrations = items
        fetchedAt = Instant.now()
    }

    /**
     * Merges one changed registration (register/cancel) into the list without ever marking it
     * fresh: a merge into a null cache seeds a partial list that is missing the attendee's other
     * registrations, so the next reader must still refetch. Only [set] receives a full answer and
     * may stamp [fetchedAt].
     */
    @Synchronized
    fun addRegistration(registration: RegistrationResponse) {
        registrations = (registrations ?: emptyList())
            .filter { it.registrationId != registration.registrationId } + registration
    }

    fun clear() {
        synchronized(this) {
            registrations = null
            fetchedAt = null
        }
    }
}