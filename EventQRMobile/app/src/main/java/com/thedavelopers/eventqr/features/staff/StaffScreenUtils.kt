package com.thedavelopers.eventqr.features.staff

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

fun String?.orUnknown(defaultValue: String = "Unknown"): String = this?.takeIf { it.isNotBlank() } ?: defaultValue

fun showToast(activity: AppCompatActivity, message: String) {
    Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
}