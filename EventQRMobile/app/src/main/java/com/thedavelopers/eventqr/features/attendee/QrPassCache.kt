package com.thedavelopers.eventqr.features.attendee

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import com.thedavelopers.eventqr.core.api.dto.RegistrationStatus
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse

/**
 * The last rendered pass, so an attendee can open it without a connection. It lives in the
 * encrypted session store, which means logout wipes it with the session, and it expires when the
 * event ends.
 *
 * Every call here touches the Keystore on read, so the pass screens call it off the main thread.
 */
data class CachedQrPass(
    val qrValue: String,
    val eventName: String,
    val serial: String,
    val meta: String,
    val eventEndAtMillis: Long,
    val savedAtMillis: Long,
    /** Written after the holder line shipped: a pass cached before it reads back as null. */
    val attendeeName: String? = null,
)

object QrPassCache {

    private const val KEY_PREFIX = "qr_pass_"
    private const val KEY_VALUE = "_value"
    private const val KEY_NAME = "_name"
    private const val KEY_SERIAL = "_serial"
    private const val KEY_META = "_meta"
    private const val KEY_ATTENDEE = "_attendee"
    private const val KEY_END = "_end"
    private const val KEY_SAVED = "_saved"

    /** The sweep runs once per process; it is not tied to any one registration. */
    @Volatile
    private var sweptThisSession = false

    fun save(context: Context, registrationId: String, pass: CachedQrPass) {
        // A blank value cannot render, so it must never become the copy every later open loads.
        if (registrationId.isBlank() || pass.qrValue.isBlank()) return
        prefs(context).edit {
            putString(key(registrationId, KEY_VALUE), pass.qrValue)
            putString(key(registrationId, KEY_NAME), pass.eventName)
            putString(key(registrationId, KEY_SERIAL), pass.serial)
            putString(key(registrationId, KEY_META), pass.meta)
            putString(key(registrationId, KEY_ATTENDEE), pass.attendeeName)
            putLong(key(registrationId, KEY_END), pass.eventEndAtMillis)
            putLong(key(registrationId, KEY_SAVED), pass.savedAtMillis)
        }
    }

    fun load(
        context: Context,
        registrationId: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): CachedQrPass? {
        if (registrationId.isBlank()) return null
        val prefs = prefs(context)
        val qrValue = prefs.getString(key(registrationId, KEY_VALUE), null).orEmpty()
        if (qrValue.isBlank()) {
            // Dropped by a build that cached before it rendered; do not keep the poisoned entry.
            clear(context, registrationId)
            return null
        }
        val eventEndAtMillis = prefs.getLong(key(registrationId, KEY_END), 0L)
        if (eventEndAtMillis <= nowMillis) {
            clear(context, registrationId)
            return null
        }
        return CachedQrPass(
            qrValue = qrValue,
            eventName = prefs.getString(key(registrationId, KEY_NAME), null).orEmpty(),
            serial = prefs.getString(key(registrationId, KEY_SERIAL), null).orEmpty(),
            meta = prefs.getString(key(registrationId, KEY_META), null).orEmpty(),
            eventEndAtMillis = eventEndAtMillis,
            savedAtMillis = prefs.getLong(key(registrationId, KEY_SAVED), 0L),
            attendeeName = prefs.getString(key(registrationId, KEY_ATTENDEE), null),
        )
    }

    fun clear(context: Context, registrationId: String) {
        if (registrationId.isBlank()) return
        prefs(context).edit { removeEntry(this, registrationId) }
    }

    fun clearCancelled(context: Context, registrations: List<RegistrationResponse>) {
        registrations
            .filter { it.status == RegistrationStatus.CANCELLED }
            .forEach { clear(context, it.registrationId.toString()) }
    }

    /**
     * Drops every copy whose event has ended, whatever the registration state. Without it a revoked
     * pass or a finished event keeps its QR value on disk until that one screen reads it again.
     */
    fun sweepExpired(context: Context, nowMillis: Long = System.currentTimeMillis()) {
        if (sweptThisSession) return
        sweptThisSession = true
        val prefs = prefs(context)
        val expired = prefs.all.keys
            .filter { it.startsWith(KEY_PREFIX) && it.endsWith(KEY_END) }
            .filter { prefs.getLong(it, 0L) <= nowMillis }
            .map { it.removePrefix(KEY_PREFIX).removeSuffix(KEY_END) }
        if (expired.isEmpty()) return
        prefs.edit { expired.forEach { removeEntry(this, it) } }
    }

    @VisibleForTesting
    internal fun resetSweepForTests() {
        sweptThisSession = false
    }

    private fun removeEntry(editor: SharedPreferences.Editor, registrationId: String) {
        editor.remove(key(registrationId, KEY_VALUE))
        editor.remove(key(registrationId, KEY_NAME))
        editor.remove(key(registrationId, KEY_SERIAL))
        editor.remove(key(registrationId, KEY_META))
        editor.remove(key(registrationId, KEY_ATTENDEE))
        editor.remove(key(registrationId, KEY_END))
        editor.remove(key(registrationId, KEY_SAVED))
    }

    private fun key(registrationId: String, suffix: String): String = KEY_PREFIX + registrationId + suffix

    private fun prefs(context: Context) = SessionManager.encryptedPrefs(context)
}
