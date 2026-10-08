package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.ApiResponse
import com.thedavelopers.eventqr.core.api.dto.PageResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadAllPagesTest {

    private fun page(items: List<Int>, last: Boolean) =
        ApiResponse(success = true, data = PageResponse(content = items, last = last, empty = items.isEmpty()))

    @Test
    fun allPagesSucceed_returnsEverything() = runBlocking {
        val result = loadAllPages<Int, Int>(10) { p -> if (p == 0) page(listOf(1, 2), false) else page(listOf(3), true) }

        assertEquals(listOf(1, 2, 3), (result as NetworkResult.Success).data)
    }

    @Test
    fun failureOnSecondPage_returnsErrorNotPartialSuccess() = runBlocking {
        val result = loadAllPages<Int, Int>(10) { p ->
            when (p) {
                0 -> page(listOf(1, 2), false)
                1 -> throw java.io.IOException("boom")
                else -> page(listOf(5), true)
            }
        }

        assertTrue(result is NetworkResult.Error)
    }

    @Test
    fun repeatedPage_stopsLoopWithoutDuplicates() = runBlocking {
        var calls = 0
        val result = loadAllPages<Int, Int>(10) { _ -> calls++; page(listOf(1, 2), false) }

        assertEquals(listOf(1, 2), (result as NetworkResult.Success).data)
        assertEquals(2, calls)
    }

    @Test
    fun sortKey_sortsAscendingAcrossPages() = runBlocking {
        val result = loadAllPages<Int, Int>(10, sortKey = { it }) { p ->
            if (p == 0) page(listOf(5, 1), false) else page(listOf(3, 2), true)
        }

        assertEquals(listOf(1, 2, 3, 5), (result as NetworkResult.Success).data)
    }

    @Test
    fun hittingMaxPagesWithoutLast_returnsErrorNotTruncatedSuccess() = runBlocking {
        var calls = 0
        val result = loadAllPages<Int, Int>(3) { p -> calls++; page(listOf(p * 10 + 1, p * 10 + 2), false) }

        assertTrue(result is NetworkResult.Error)
        assertEquals(3, calls)
    }

    @Test
    fun lastPageOnFinalAllowedPage_isStillSuccess() = runBlocking {
        val result = loadAllPages<Int, Int>(2) { p -> if (p == 0) page(listOf(1), false) else page(listOf(2), true) }

        assertEquals(listOf(1, 2), (result as NetworkResult.Success).data)
    }

    @Test
    fun sortKey_instantsWithMixedOffsetsSortByInstantNotText() = runBlocking {
        fun at(s: String) = com.thedavelopers.eventqr.core.api.InstantTypeAdapter.parseInstant(s)
        // 10:00+08:00 = 02:00Z, which is EARLIER than 05:00Z although it sorts later as text.
        val items = listOf(
            "z-05" to at("2026-01-01T05:00:00Z"),
            "plus8-10" to at("2026-01-01T10:00:00+08:00"),
            "none" to null,
            "z-03" to at("2026-01-01T03:00:00Z"),
            "minus5-00" to at("2026-01-01T00:00:00-05:00"),
        )
        val result = loadAllPages<Pair<String, java.time.Instant?>, java.time.Instant>(10, sortKey = { it.second }) { _ ->
            ApiResponse(success = true, data = PageResponse(content = items, last = true, empty = false))
        }

        // 02:00Z, 03:00Z, 05:00Z, 05:00Z... minus5-00 = 05:00Z ties with z-05 and keeps input order (z-05 first).
        assertEquals(listOf("plus8-10", "z-03", "z-05", "minus5-00", "none"), (result as NetworkResult.Success).data.map { it.first })
    }

    @Test
    fun sortKey_nullsLastAndStable()= runBlocking {
        // Pairs of (id, key): equal keys and nulls must keep their original relative order.
        val items = listOf("a" to null, "b" to 2, "c" to null, "d" to 1, "e" to 2)
        val result = loadAllPages<Pair<String, Int?>, Int>(10, sortKey = { it.second }) { _ ->
            ApiResponse(success = true, data = PageResponse(content = items, last = true, empty = false))
        }

        val ids = (result as NetworkResult.Success).data.map { it.first }
        assertEquals(listOf("d", "b", "e", "a", "c"), ids)
    }
}
