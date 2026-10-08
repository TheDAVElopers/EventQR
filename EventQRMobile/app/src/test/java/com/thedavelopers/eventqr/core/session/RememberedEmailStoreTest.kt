package com.thedavelopers.eventqr.core.session

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class RememberedEmailStoreTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun store() = RememberedEmailStore(context.getSharedPreferences("remembered_email_test", Context.MODE_PRIVATE))

    @Test
    fun saveThenGet_roundTripsTheTrimmedEmail() {
        val store = store()
        assertNull(store.get())

        store.save("  user@example.com  ")

        assertEquals("user@example.com", store.get())
    }

    @Test
    fun blankEmail_isIgnoredAndKeepsThePreviousValue() {
        val store = store()
        store.save("user@example.com")

        store.save("   ")

        assertEquals("user@example.com", store.get())
    }

    @Test
    fun clear_forgetsTheEmail() {
        val store = store()
        store.save("user@example.com")

        store.clear()

        assertNull(store.get())
    }

    @Test
    fun onlyTheEmailKeyIsEverWritten_noPasswordOrTokenFields() {
        val prefs = context.getSharedPreferences("remembered_email_keys", Context.MODE_PRIVATE)
        RememberedEmailStore(prefs).save("user@example.com")

        assertEquals(setOf("email"), prefs.all.keys)
    }

    @Test
    fun storeWithoutSecureStorage_isASafeNoOp() {
        val store = RememberedEmailStore(null)

        store.save("user@example.com")
        store.clear()

        assertNull(store.get())
    }

    @Test
    fun rememberedEmailSurvivesClearingTheSession() {
        val remembered = RememberedEmailStore.create(context)
        remembered.save("user@example.com")

        SessionManager(context).clearSession()

        assertEquals("user@example.com", RememberedEmailStore.create(context).get())
        assertFalse(RememberedEmailStore.PREFS_NAME == SessionManager.PREFS_NAME)
    }
}
