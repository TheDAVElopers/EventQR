package com.thedavelopers.eventqr.features.auth.forgotpassword

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.auth.login.LoginActivity

open class ForgotPasswordActivity : AppCompatActivity(), ForgotPasswordContract.View {
    private lateinit var presenter: ForgotPasswordPresenter
    private lateinit var emailInput: EditText
    private lateinit var sendButton: Button
    private lateinit var backButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        presenter = ForgotPasswordPresenter(this)
        presenter.attach(this)

        emailInput = findViewById(R.id.editEmail)
        sendButton = findViewById(R.id.btnSendResetLink)
        backButton = findViewById(R.id.btnBackToSignIn)

        sendButton.setOnClickListener {
            presenter.submitRequest(emailInput.text.toString())
        }

        backButton.setOnClickListener {
            presenter.backToSignIn()
        }
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }

    override fun showLoading(isLoading: Boolean) {
        sendButton.isEnabled = !isLoading
        sendButton.text = if (isLoading) "Sending..." else "Send Reset Link"
    }

    override fun showEmailError(message: String?) {
        emailInput.error = message
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun navigateBackToSignIn() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}