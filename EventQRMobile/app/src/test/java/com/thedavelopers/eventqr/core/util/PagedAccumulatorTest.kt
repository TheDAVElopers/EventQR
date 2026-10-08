package com.thedavelopers.eventqr.core.util

import com.thedavelopers.eventqr.core.api.dto.PageResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PagedAccumulatorTest {
    private fun page(items: List<Int>, last: Boolean, total: Long = 99) =
        PageResponse(content = items, totalElements = total, last = last, empty = items.isEmpty())

    private fun acc() = PagedAccumulator<Int, Int> { it }

    @Test
    fun pagesAreAppendedInOrder() {
        val a = acc()
        val t0 = a.begin()!!
        assertEquals(0, t0.page)
        a.onSuccess(t0, page(listOf(1, 2), last = false))
        val t1 = a.begin()!!
        assertEquals(1, t1.page)
        a.onSuccess(t1, page(listOf(3), last = false))
        assertEquals(listOf(1, 2, 3), a.items)
        assertTrue(a.canLoadMore())
    }

    @Test
    fun stopsAtLastPage() {
        val a = acc()
        a.onSuccess(a.begin()!!, page(listOf(1), last = true))
        assertTrue(a.isLast)
        assertNull(a.begin())
    }

    @Test
    fun duplicateOnlyPageIsNeverAppendedButDoesNotEndTheList() {
        val a = acc()
        a.onSuccess(a.begin()!!, page(listOf(1, 2), last = false))
        a.onSuccess(a.begin()!!, page(listOf(1, 2), last = false))
        assertEquals(listOf(1, 2), a.items)
        assertFalse(a.isLast)
        a.onSuccess(a.begin()!!, page(listOf(3, 4), last = true))
        assertEquals(listOf(1, 2, 3, 4), a.items)
    }

    @Test
    fun threeDuplicateOnlyPagesInARowStopPaging() {
        val a = acc()
        a.onSuccess(a.begin()!!, page(listOf(1, 2), last = false))
        repeat(2) { a.onSuccess(a.begin()!!, page(listOf(1, 2), last = false)); assertFalse(a.isLast) }
        a.onSuccess(a.begin()!!, page(listOf(1, 2), last = false))
        assertTrue(a.isLast)
        assertNull(a.begin())
        assertEquals(listOf(1, 2), a.items)
    }

    @Test
    fun freshPageResetsTheDuplicateStreak() {
        val a = acc()
        a.onSuccess(a.begin()!!, page(listOf(1), last = false))
        repeat(2) { a.onSuccess(a.begin()!!, page(listOf(1), last = false)) }
        a.onSuccess(a.begin()!!, page(listOf(2), last = false))
        repeat(2) { a.onSuccess(a.begin()!!, page(listOf(1), last = false)) }
        assertFalse(a.isLast)
    }

    @Test
    fun staleResponseAfterResetThenRetryIsIgnoredAndFreshOneAccepted() {
        val a = acc()
        val stale = a.begin()!!
        a.reset()
        val fresh = a.begin()!!
        a.onFailure(fresh)
        a.retry()
        val retried = a.begin()!!
        assertFalse(a.onSuccess(stale, page(listOf(9), last = true)))
        assertFalse(a.onFailure(stale))
        assertTrue(a.onSuccess(retried, page(listOf(1), last = true)))
        assertEquals(listOf(1), a.items)
    }

    @Test
    fun emptyPageStopsPaging() {
        val a = acc()
        a.onSuccess(a.begin()!!, page(listOf(1), last = false))
        a.onSuccess(a.begin()!!, page(emptyList(), last = false))
        assertTrue(a.isLast)
    }

    @Test
    fun noSecondRequestWhileOneIsInFlight() {
        val a = acc()
        assertNotNull(a.begin())
        assertNull(a.begin())
    }

    @Test
    fun errorStopsUntilRetryThenRetriesTheSamePage() {
        val a = acc()
        a.onSuccess(a.begin()!!, page(listOf(1), last = false))
        val failing = a.begin()!!
        a.onFailure(failing)
        assertTrue(a.hasError)
        assertNull(a.begin())
        a.retry()
        val retried = a.begin()!!
        assertEquals(failing.page, retried.page)
        a.onSuccess(retried, page(listOf(2), last = true))
        assertEquals(listOf(1, 2), a.items)
    }

    @Test
    fun staleResponseAfterResetIsIgnored() {
        val a = acc()
        val old = a.begin()!!
        a.reset()
        val fresh = a.begin()!!
        assertFalse(a.onSuccess(old, page(listOf(9), last = true)))
        assertTrue(a.onSuccess(fresh, page(listOf(1), last = true)))
        assertEquals(listOf(1), a.items)
        assertEquals(0, fresh.page)
    }

    @Test
    fun remembersServerTotal() {
        val a = acc()
        a.onSuccess(a.begin()!!, page(listOf(1), last = false, total = 57))
        assertEquals(57L, a.totalElements)
    }
}
