package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.core.util.UiStrings
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationRequest
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationSubmissionResponse
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class RegistrationPresenterEmailTest {

    @Test
    fun resolveEmail_prefersOwnEmailOverField() {
        assertEquals("me@x.com", resolveRegistrationEmail(" me@x.com ", "other@x.com"))
        assertEquals("field@x.com", resolveRegistrationEmail(null, " field@x.com "))
        assertEquals("field@x.com", resolveRegistrationEmail("  ", "field@x.com"))
    }

    @Test
    fun submit_sendsSessionEmailNotFieldValue_andMapsForbidden() {
        val msg = "You can only register using your own account email"
        val repo = FakeRepo(NetworkResult.Error(msg, serverMessage = msg))
        val view = RecordingView()
        val p = RegistrationPresenter(view, repo, UiStrings(ApplicationProvider.getApplicationContext()), ownEmailProvider = { "me@x.com" }, ownEmailMessage = "OWN")

        p.submit(UUID.randomUUID().toString(), "Jane", "tampered@x.com", "639171234567")
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf("me@x.com"), repo.emails)
        assertEquals(listOf("OWN"), view.messages)
    }

    private class FakeRepo(private val result: NetworkResult<RegistrationSubmissionResponse>) :
        AttendeeRepository(ApplicationProvider.getApplicationContext()) {
        val emails = mutableListOf<String>()
        override suspend fun createRegistration(request: RegistrationRequest): NetworkResult<RegistrationSubmissionResponse> {
            emails += request.email
            return result
        }
    }

    private class RecordingView : RegistrationContract.View {
        val messages = mutableListOf<String>()
        override fun showLoading(isLoading: Boolean) = Unit
        override fun showMessage(message: String) { messages += message }
        override fun showFieldError(field: String, message: String?) = Unit
        override fun openQr(registrationId: String, qrCredentialId: String) = Unit
    }
}
