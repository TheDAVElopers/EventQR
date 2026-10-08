package com.thedavelopers.eventqr.core.util

import android.content.Context
import androidx.annotation.StringRes

/** Resolves string resources for classes that have no Context of their own (presenters, view models). */
class UiStrings(context: Context) {
    private val app = context.applicationContext

    fun get(@StringRes id: Int, vararg args: Any): String = app.getString(id, *args)
}
