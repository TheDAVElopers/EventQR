package com.thedavelopers.eventqr.features.auth.changepassword

interface ChangePasswordContract {
    interface View {
        fun showLoading(isLoading: Boolean)
        fun showCurrentPasswordError(message: String?)
        fun showNewPasswordError(message: String?)
        fun showConfirmPasswordError(message: String?)
        /**
         * A failure of the whole request: one sentence in the §5.16 strip above the footer, never a `Retry`
         * (a repeat after a lost answer would send a current password the server has already replaced).
         */
        fun showFormError(message: String)
        fun showSuccess()
        fun navigateBack()
    }
}
