package com.thedavelopers.eventqr.features.auth.resetpassword

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class ResetPasswordPresenterTest {

    private class RecordingView : ResetPasswordContract.View {
        val passwordErrors = mutableListOf<String?>()
        val loading = mutableListOf<Boolean>()
        override fun showLoading(isLoading: Boolean) { loading += isLoading }
        override fun showTokenInvalid() = Unit
        override fun showForm() = Unit
        override fun showPasswordError(message: String?) { passwordErrors += message }
        override fun showConfirmPasswordError(message: String?) = Unit
        override fun showMessage(message: String) = Unit
        override fun showSuccess() = Unit
        override fun navigateToLogin() = Unit
    }

    @Test
    fun submitReset_noLowercase_rejectedWithoutNetworkCall() {
        val view = RecordingView()
        val presenter = ResetPasswordPresenter()
        presenter.attach(view, ApplicationProvider.getApplicationContext())
        // Token is normally set by validateToken (network); set it directly to reach the validation branch.
        presenter.token = "tok"

        presenter.submitReset("PASSWORD1!", "PASSWORD1!")

        assertEquals(
            "Password must be at least 8 characters and include an uppercase letter, a lowercase letter, a number, and a special character",
            view.passwordErrors.last(),
        )
        assertTrue(view.loading.isEmpty())
    }
}
