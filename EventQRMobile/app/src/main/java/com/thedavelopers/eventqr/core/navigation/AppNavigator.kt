package com.thedavelopers.eventqr.core.navigation

import android.content.Context
import android.content.Intent
import com.thedavelopers.eventqr.features.auth.changepassword.ChangePasswordActivity
import com.thedavelopers.eventqr.features.auth.forgotpassword.ForgotPasswordActivity
import com.thedavelopers.eventqr.features.auth.login.LoginActivity
import com.thedavelopers.eventqr.features.auth.register.RegistrationActivity
import com.thedavelopers.eventqr.features.dashboard.DashboardActivity
import com.thedavelopers.eventqr.features.landing.LandingActivity

object AppNavigator {
    fun landing(context: Context): Intent = Intent(context, LandingActivity::class.java)

    fun login(context: Context): Intent = Intent(context, LoginActivity::class.java)

    fun register(context: Context): Intent = Intent(context, RegistrationActivity::class.java)

    fun forgotPassword(context: Context): Intent = Intent(context, ForgotPasswordActivity::class.java)

    fun changePassword(context: Context): Intent = Intent(context, ChangePasswordActivity::class.java)

    fun dashboard(context: Context): Intent = Intent(context, DashboardActivity::class.java)
}
