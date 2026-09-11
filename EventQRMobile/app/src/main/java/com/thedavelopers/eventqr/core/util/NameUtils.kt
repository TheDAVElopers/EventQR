package com.thedavelopers.eventqr.core.util

fun String.firstNameOnly(): String =
    trim().split(Regex("\\s+")).firstOrNull()?.takeIf { it.isNotBlank() } ?: this