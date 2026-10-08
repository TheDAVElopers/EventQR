package com.thedavelopers.eventqr.core.util

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Calls [onNearEnd] when the user scrolls within [threshold] rows of the end of a vertical list. */
fun RecyclerView.addNearEndListener(threshold: Int = 5, onNearEnd: () -> Unit) {
    addOnScrollListener(object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (dy <= 0) return
            val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
            val first = lm.findFirstVisibleItemPosition()
            if (first >= 0 && lm.childCount + first >= lm.itemCount - threshold) onNearEnd()
        }
    })
}
