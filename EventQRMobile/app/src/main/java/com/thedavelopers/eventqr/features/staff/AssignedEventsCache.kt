package com.thedavelopers.eventqr.features.staff

import com.thedavelopers.eventqr.features.staff.model.dto.StaffAssignedEventResponse

/**
 * In-memory copy of the caller's assigned-event list, shared by every [StaffRepository] (each screen builds its own)
 * so that opening several staff screens in a row does not re-download it. Only successful responses are kept, and
 * they expire after [ttlMs]. Cleared on pull-to-refresh and whenever the session changes.
 */
class AssignedEventsCache(
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class Entry(val events: List<StaffAssignedEventResponse>, val storedAtMs: Long)

    @Volatile
    private var entry: Entry? = null

    /** Bumped by [clear]; a fetch started before a clear must not store its result afterwards. */
    @Volatile
    private var generation = 0

    /** Read before starting a fetch and passed back to [put]. */
    fun generation(): Int = generation

    /** The cached list, or null when nothing is cached or it is older than the TTL. */
    fun get(): List<StaffAssignedEventResponse>? {
        val current = entry ?: return null
        return if (clock() - current.storedAtMs < ttlMs) current.events else null
    }

    /** Stores [events] unless [clear] ran since [fetchGeneration] was read (a stale previous-session result). */
    @Synchronized
    fun put(events: List<StaffAssignedEventResponse>, fetchGeneration: Int = generation) {
        if (fetchGeneration != generation) return
        entry = Entry(events, clock())
    }

    @Synchronized
    fun clear() {
        generation++
        entry = null
    }

    companion object {
        const val DEFAULT_TTL_MS = 60_000L

        /** The process-wide instance used by the app. */
        val shared = AssignedEventsCache()
    }
}
