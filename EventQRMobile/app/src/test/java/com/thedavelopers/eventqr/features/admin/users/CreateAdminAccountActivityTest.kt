package com.thedavelopers.eventqr.features.admin.users

import android.os.Looper
import android.widget.Button
import android.widget.EditText
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.features.admin.AdminRepository
import com.thedavelopers.eventqr.features.users.model.dto.UserResponse
import org.robolectric.Shadows.shadowOf
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/** SA-3: the phone field accepts the "+63 912 345 6789" format its own hint shows. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class CreateAdminAccountActivityTest {

    @Before
    fun setUp() {
        SessionManager(ApplicationProvider.getApplicationContext()).apply {
            clearSession()
            saveRole(AccountRole.SUPER_ADMIN)
        }
    }

    private fun submitWithPhone(phone: String): EditText {
        val activity = Robolectric.buildActivity(CreateAdminAccountActivity::class.java).create().get()
        val phoneInput = activity.findViewById<EditText>(R.id.inputAdminPhone)
        phoneInput.setText(phone)
        // Other fields stay blank so validation fails before any network call; only the phone error matters here.
        activity.findViewById<Button>(R.id.btnCreateAdminAccount).performClick()
        return phoneInput
    }

    @Test
    fun hintFormatWithSpaces_passesPhoneValidation() {
        assertNull(submitWithPhone("+63 912 345 6789").error)
    }

    @Test
    fun dashedAndPlainDigitForms_passPhoneValidation() {
        assertNull(submitWithPhone("+63-912-345-6789").error)
        assertNull(submitWithPhone("639123456789").error)
    }

    @Test
    fun hintFormat_isSentToTheBackendAsE164() {
        val activity = Robolectric.buildActivity(CreateAdminAccountActivity::class.java).create().get()
        val repo = CapturingRepo()
        activity.repository = repo
        activity.findViewById<EditText>(R.id.inputAdminFirstName).setText("Ana")
        activity.findViewById<EditText>(R.id.inputAdminLastName).setText("Cruz")
        activity.findViewById<EditText>(R.id.inputAdminEmail).setText("ana@example.com")
        activity.findViewById<EditText>(R.id.inputAdminPhone).setText("+63 912 345 6789")
        activity.findViewById<EditText>(R.id.inputAdminPassword).setText("Passw0rd!")
        activity.findViewById<EditText>(R.id.inputAdminConfirmPassword).setText("Passw0rd!")

        activity.findViewById<Button>(R.id.btnCreateAdminAccount).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf<String?>("+639123456789"), repo.phones)
    }

    private class CapturingRepo : AdminRepository(ApplicationProvider.getApplicationContext()) {
        val phones = mutableListOf<String?>()
        override suspend fun createAdminAccount(
            fullName: String,
            email: String,
            phoneNumber: String?,
            password: String,
        ): NetworkResult<UserResponse> {
            phones += phoneNumber
            return NetworkResult.Error("stop here")
        }
    }

    @Test
    fun shortNumber_isStillRejected() {
        val phoneInput = submitWithPhone("+63 912 345")
        assertEquals(
            phoneInput.context.getString(R.string.error_invalid_phone),
            phoneInput.error?.toString(),
        )
    }
}
