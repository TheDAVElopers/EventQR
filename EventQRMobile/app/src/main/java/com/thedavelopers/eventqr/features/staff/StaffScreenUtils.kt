package com.thedavelopers.eventqr.features.staff

fun String?.orUnknown(defaultValue: String = "Unknown"): String = this?.takeIf { it.isNotBlank() } ?: defaultValue