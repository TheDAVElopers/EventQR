package com.thedavelopers.eventqr.core.util

import com.thedavelopers.eventqr.core.api.dto.PageResponse

/**
 * Accumulates server pages for an endless-scroll list.
 *
 * - never appends a row whose key is already present (repeated page / overlapping pages)
 * - stops at `last`, at an empty page, or after 3 consecutive pages that add nothing new
 * - stops after a failure until [retry] is called
 * - a [reset] (filter / search change) invalidates in-flight responses via a generation counter
 */
class PagedAccumulator<T, K>(private val keyOf: (T) -> K) {
    /** Handle for one in-flight request; pass it back to [onSuccess] / [onFailure]. */
    data class Ticket(val generation: Int, val page: Int)

    private val rows = mutableListOf<T>()
    private val seen = HashSet<K>()
    private var generation = 0
    private var nextPage = 0
    private var duplicateOnlyStreak = 0

    var isLast = false
        private set
    var hasError = false
        private set
    var isLoading = false
        private set
    var totalElements = 0L
        private set

    val items: List<T> get() = rows.toList()
    val isEmpty: Boolean get() = rows.isEmpty()

    fun canLoadMore(): Boolean = !isLast && !isLoading && !hasError

    fun reset() {
        generation++
        rows.clear()
        seen.clear()
        nextPage = 0
        duplicateOnlyStreak = 0
        isLast = false
        hasError = false
        isLoading = false
        totalElements = 0
    }

    /** Clears a previous failure so the next [begin] retries the same page. */
    fun retry() {
        hasError = false
    }

    /** Returns a ticket for the next page, or null when nothing should be loaded. */
    fun begin(): Ticket? {
        if (!canLoadMore()) return null
        isLoading = true
        return Ticket(generation, nextPage)
    }

    /** Applies a page. Returns false when the response was stale and ignored. */
    fun onSuccess(ticket: Ticket, page: PageResponse<T>): Boolean {
        if (ticket.generation != generation) return false
        isLoading = false
        var added = 0
        for (row in page.content) {
            if (seen.add(keyOf(row))) {
                rows.add(row)
                added++
            }
        }
        totalElements = page.totalElements
        nextPage = ticket.page + 1
        // A page of only already-seen rows (new scans pushed rows down) is skipped, not the end; but give up
        // after MAX_DUPLICATE_ONLY_PAGES in a row so a backend ignoring `page` cannot loop forever.
        if (page.content.isNotEmpty() && added == 0) duplicateOnlyStreak++ else duplicateOnlyStreak = 0
        if (page.last || page.content.isEmpty() || duplicateOnlyStreak >= MAX_DUPLICATE_ONLY_PAGES) isLast = true
        return true
    }

    fun onFailure(ticket: Ticket): Boolean {
        if (ticket.generation != generation) return false
        isLoading = false
        hasError = true
        return true
    }

    companion object {
        const val MAX_DUPLICATE_ONLY_PAGES = 3
    }
}
