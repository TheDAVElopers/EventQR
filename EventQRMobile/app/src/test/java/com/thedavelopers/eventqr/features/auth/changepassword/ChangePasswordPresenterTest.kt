package com.thedavelopers.eventqr.features.auth.changepassword

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
class ChangePasswordPresenterTest {

    private class RecordingView : ChangePasswordContract.View {
        val newPasswordErrors = mutableListOf<String?>()
        val loading = mutableListOf<Boolean>()
        override fun showLoading(isLoading: Boolean) { loading += isLoading }
        override fun showCurrentPasswordError(message: String?) = Unit
        override fun showNewPasswordError(message: String?) { newPasswordErrors += message }
        override fun showConfirmPasswordError(message: String?) = Unit
        override fun showMessage(message: String) = Unit
        override fun showSuccess() = Unit
        override fun navigateBack() = Unit
    }

    @Test
    fun submitChange_noLowercase_rejectedWithoutNetworkCall() {
        val view = RecordingView()
        val presenter = ChangePasswordPresenter()
        presenter.attach(view, ApplicationProvider.getApplicationContext())

        presenter.submitChange("OldPass1!", "PASSWORD1!", "PASSWORD1!")

        assertEquals(
            "Password must be at least 8 characters and include an uppercase letter, a lowercase letter, a number, and a special character",
            view.newPasswordErrors.last(),
        )
        assertTrue(view.loading.isEmpty())
    }
}
