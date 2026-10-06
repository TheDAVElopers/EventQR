package com.thedavelopers.eventqr.features

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/**
 * Robolectric cannot provide the platform AndroidKeyStore, so SessionManager's
 * EncryptedSharedPreferences-backed storage throws in unit tests. These shadows swap
 * in plain SharedPreferences with the same file name so guard tests can exercise the
 * real activities. Test-only: production code is untouched.
 */
@Implements(MasterKey.Builder::class)
class ShadowMasterKeyBuilder {
    @Implementation
    fun build(): MasterKey = allocateMasterKey()
}

@Implements(EncryptedSharedPreferences::class)
object ShadowEncryptedSharedPreferences {
    @JvmStatic
    @Implementation
    fun create(
        context: Context,
        fileName: String,
        masterKey: MasterKey,
        keyEncryptionScheme: EncryptedSharedPreferences.PrefKeyEncryptionScheme,
        valueEncryptionScheme: EncryptedSharedPreferences.PrefValueEncryptionScheme,
    ): SharedPreferences = context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
}

private fun allocateMasterKey(): MasterKey {
    val unsafe = Class.forName("sun.misc.Unsafe")
        .getDeclaredField("theUnsafe")
        .apply { isAccessible = true }
        .get(null)
    val allocate = unsafe.javaClass.getMethod("allocateInstance", Class::class.java)
    return allocate.invoke(unsafe, MasterKey::class.java) as MasterKey
}
